package com.tashlark.app.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.tashlark.app.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * One-time "Remove ads" purchase (Play product id [PRODUCT_REMOVE_ADS], type INAPP).
 * The entitlement is cached in settings so it also works offline.
 */
class BillingManager(context: Context, private val settings: SettingsRepository) : PurchasesUpdatedListener {

    sealed interface Status {
        data object Unavailable : Status
        data class Ready(val price: String) : Status
        data object Pending : Status
        data object Owned : Status
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _status = MutableStateFlow<Status>(Status.Unavailable)
    val status: StateFlow<Status> = _status.asStateFlow()

    private var details: ProductDetails? = null
    private var connecting = false

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    fun connect() {
        if (client.isReady) { scope.launch { refresh() }; return }
        if (connecting) return
        connecting = true
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                connecting = false
                if (result.responseCode == BillingClient.BillingResponseCode.OK) scope.launch { refresh() }
            }
            override fun onBillingServiceDisconnected() { connecting = false }
        })
    }

    private suspend fun refresh() {
        runCatching {
            val purchases = client.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),
            )
            if (purchases.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                handle(purchases.purchasesList, fromQuery = true)
            }
            if (_status.value !is Status.Owned && _status.value !is Status.Pending) {
                val result = client.queryProductDetails(
                    QueryProductDetailsParams.newBuilder().setProductList(
                        listOf(
                            QueryProductDetailsParams.Product.newBuilder()
                                .setProductId(PRODUCT_REMOVE_ADS)
                                .setProductType(BillingClient.ProductType.INAPP)
                                .build(),
                        ),
                    ).build(),
                )
                val d = result.productDetailsList?.firstOrNull()
                details = d
                val price = d?.oneTimePurchaseOfferDetails?.formattedPrice
                _status.value = if (price != null) Status.Ready(price) else Status.Unavailable
            }
        }.onFailure { Log.w(TAG, "Billing refresh failed", it) }
    }

    /** Starts the Play purchase sheet. Returns false if the product isn't available yet. */
    fun buy(activity: Activity): Boolean {
        val d = details ?: return false
        val params = BillingFlowParams.newBuilder().setProductDetailsParamsList(
            listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).build()),
        ).build()
        return client.launchBillingFlow(activity, params).responseCode == BillingClient.BillingResponseCode.OK
    }

    fun restore() = connect()

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            scope.launch { handle(purchases, fromQuery = false) }
        } else if (result.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
            scope.launch { refresh() }
        }
    }

    private suspend fun handle(purchases: List<Purchase>, fromQuery: Boolean) {
        val mine = purchases.filter { PRODUCT_REMOVE_ADS in it.products }
        val owned = mine.firstOrNull { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        when {
            owned != null -> {
                if (!owned.isAcknowledged) {
                    client.acknowledgePurchase(
                        AcknowledgePurchaseParams.newBuilder().setPurchaseToken(owned.purchaseToken).build(),
                    )
                }
                settings.setAdsRemoved(true)
                _status.value = Status.Owned
            }
            mine.any { it.purchaseState == Purchase.PurchaseState.PENDING } -> _status.value = Status.Pending
            fromQuery -> {
                // Play says the player doesn't own it (e.g. refunded): reflect that everywhere.
                settings.setAdsRemoved(false)
                if (_status.value is Status.Owned || _status.value is Status.Pending) _status.value = Status.Unavailable
            }
        }
    }

    companion object {
        private const val TAG = "Billing"
        const val PRODUCT_REMOVE_ADS = "remove_ads"
    }
}
