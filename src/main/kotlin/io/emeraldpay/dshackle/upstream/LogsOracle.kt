package io.emeraldpay.dshackle.upstream

import io.emeraldpay.dshackle.config.IndexConfig
import org.slf4j.LoggerFactory
import org.springframework.cloud.sleuth.Tracer
import reactor.core.Disposable
import reactor.core.publisher.Mono
import reactor.core.scheduler.Scheduler

class LogsOracle(
    private val config: IndexConfig.Index,
    private val upstream: Multistream,
    private val scheduler: Scheduler,
    private val tracer: Tracer,
) {

    private val log = LoggerFactory.getLogger(LogsOracle::class.java)

    private var subscription: Disposable? = null
    private var conn: org.drpc.logsoracle.LogsOracle? = null

    fun start() {
        log.info("liboracle starting: store=${config.store}, ram=${config.ram_limit}")

        conn = org.drpc.logsoracle.LogsOracle(config.store, config.ram_limit ?: 0L)
        subscription = upstream.getHead().getFlux()
            .publishOn(scheduler)
            .doOnError { t -> log.error("Failed to subscribe head for oracle", t) }
            .doFinally { log.info("unsubscribe head") }
            .subscribe { setHeight(it.height) }

        setUpstream(config.rpc)
    }

    fun stop() {
        log.info("liboracle closed")

        subscription?.dispose()
        subscription = null

        conn?.close()
    }

    fun estimate(
        limit: Long?,
        fromBlock: Long,
        toBlock: Long,
        address: List<String>,
        topics: List<List<String>>,
    ): Mono<String> {
        val requestSpan = tracer.currentSpan()

        return Mono.fromCallable {
            val span = tracer.nextSpan(requestSpan).name("emerald.blockchain/logsoracle").start()
            log.info("query: from=$fromBlock, to=$toBlock, limit=$limit")

            try {
                val estimate = conn?.Query(limit, fromBlock, toBlock, address, topics)
                "{\"total\":$estimate,\"overflow\":false}"
            } catch (e: org.drpc.logsoracle.LogsOracle.LogsOracleException) {
                if (e.isQueryOverflow()) {
                    "{\"total\":-1,\"overflow\":true}"
                } else {
                    throw e
                }
            } finally {
                span.end()
            }
        }
            .publishOn(scheduler)
    }

    fun setHeight(height: Long) {
        try {
            log.info("update state: height=$height")
            conn?.UpdateHeight(height)
        } catch (e: Exception) {
            log.error("couldn't set height", e)
        }
    }

    fun setUpstream(upstream: String) {
        try {
            log.info("update state: upstream=$upstream")
            conn?.SetUpstream(upstream)
        } catch (e: Exception) {
            log.error("couldn't set upstream", e)
        }
    }
}
