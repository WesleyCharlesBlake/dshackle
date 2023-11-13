/**
 * Copyright (c) 2022 EmeraldPay, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.emeraldpay.dshackle.upstream.ethereum.subscribe

import io.emeraldpay.dshackle.upstream.IngressSubscription
import io.emeraldpay.dshackle.upstream.SubscriptionConnect
import io.emeraldpay.dshackle.upstream.ethereum.EthereumEgressSubscription
import io.emeraldpay.dshackle.upstream.ethereum.EthereumIngressSubscription
import io.emeraldpay.dshackle.upstream.ethereum.WsSubscriptions

class EthereumWsIngressSubscription(
    conn: WsSubscriptions,
) : IngressSubscription, EthereumIngressSubscription {

    private val pendingTxes = WebsocketPendingTxes(conn)

    override fun getAvailableTopics(): List<String> {
        return listOf(EthereumEgressSubscription.METHOD_PENDING_TXES)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> get(topic: String, params: Any?): SubscriptionConnect<T>? {
        if (topic == EthereumEgressSubscription.METHOD_PENDING_TXES) {
            return pendingTxes as SubscriptionConnect<T>
        }
        return null
    }

    override fun getPendingTxes(): PendingTxesSource {
        return pendingTxes
    }
}
