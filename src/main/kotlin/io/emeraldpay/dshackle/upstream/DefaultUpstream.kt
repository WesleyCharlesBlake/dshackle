/**
 * Copyright (c) 2020 EmeraldPay, Inc
 * Copyright (c) 2019 ETCDEV GmbH
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
package io.emeraldpay.dshackle.upstream

import io.emeraldpay.api.proto.BlockchainOuterClass
import io.emeraldpay.dshackle.config.UpstreamsConfig
import io.emeraldpay.dshackle.startup.QuorumForLabels
import io.emeraldpay.dshackle.upstream.calls.CallMethods
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import java.util.concurrent.atomic.AtomicReference

abstract class DefaultUpstream(
    private val id: String,
    private val hash: Byte,
    defaultLag: Long,
    defaultAvail: UpstreamAvailability,
    private val options: UpstreamsConfig.Options,
    private val role: UpstreamsConfig.UpstreamRole,
    private val targets: CallMethods?,
    private val node: QuorumForLabels.QuorumItem?
) : Upstream {

    constructor(
        id: String,
        hash: Byte,
        options: UpstreamsConfig.Options,
        role: UpstreamsConfig.UpstreamRole,
        targets: CallMethods?
    ) :
        this(
            id,
            hash,
            Long.MAX_VALUE,
            UpstreamAvailability.UNAVAILABLE,
            options,
            role,
            targets,
            QuorumForLabels.QuorumItem.empty()
        )

    constructor(
        id: String,
        hash: Byte,
        options: UpstreamsConfig.Options,
        role: UpstreamsConfig.UpstreamRole,
        targets: CallMethods?,
        node: QuorumForLabels.QuorumItem?
    ) :
        this(id, hash, Long.MAX_VALUE, UpstreamAvailability.UNAVAILABLE, options, role, targets, node)

    private val status = AtomicReference(Status(defaultLag, defaultAvail, statusByLag(defaultLag, defaultAvail)))
    private val statusStream = Sinks.many()
        .multicast()
        .directBestEffort<UpstreamAvailability>()

    init {
        if (id.length < 3 || !id.matches(Regex("[a-zA-Z][a-zA-Z0-9_-]+[a-zA-Z0-9]"))) {
            throw IllegalArgumentException("Invalid upstream id: $id")
        }
    }

    override fun isAvailable(): Boolean {
        return getStatus() == UpstreamAvailability.OK
    }

    fun onStatus(value: BlockchainOuterClass.ChainStatus) {
        val available = value.availability
        setStatus(
            if (available != null) UpstreamAvailability.fromGrpc(available.number) else UpstreamAvailability.UNAVAILABLE
        )
    }

    override fun getStatus(): UpstreamAvailability {
        return status.get().status
    }

    open fun setStatus(avail: UpstreamAvailability) {
        status.updateAndGet { curr ->
            Status(curr.lag, avail, statusByLag(curr.lag, avail))
        }.also {
            statusStream.tryEmitNext(it.status)
        }
    }

    private fun statusByLag(lag: Long, proposed: UpstreamAvailability): UpstreamAvailability {
        if (options.disableValidation == true) {
            // if we specifically told that this upstream should be _always valid_ then skip
            // the status calculation and trust the proposed value as is
            return proposed
        }
        return if (proposed == UpstreamAvailability.OK) {
            when {
                lag > 6 -> UpstreamAvailability.SYNCING
                lag > 1 -> UpstreamAvailability.LAGGING
                else -> proposed
            }
        } else proposed
    }

    override fun observeStatus(): Flux<UpstreamAvailability> {
        return statusStream.asFlux()
            .distinctUntilChanged()
    }

    override fun setLag(lag: Long) {
        lag.coerceAtLeast(0).let { nLag ->
            status.updateAndGet { curr ->
                Status(nLag, curr.avail, statusByLag(nLag, curr.avail))
            }.also {
                statusStream.tryEmitNext(it.status)
            }
        }
    }

    override fun getLag(): Long {
        return this.status.get().lag
    }

    override fun getId(): String {
        return id
    }

    override fun getOptions(): UpstreamsConfig.Options {
        return options
    }

    override fun getRole(): UpstreamsConfig.UpstreamRole {
        return role
    }

    override fun getMethods(): CallMethods {
        return targets ?: throw IllegalStateException("Methods are not set")
    }

    override fun nodeId(): Byte = hash

    private val quorumByLabel = node?.let { QuorumForLabels(it) }
        ?: QuorumForLabels(QuorumForLabels.QuorumItem.empty())

    open fun getQuorumByLabel(): QuorumForLabels {
        return quorumByLabel
    }

    class Status(val lag: Long, val avail: UpstreamAvailability, val status: UpstreamAvailability)
}
