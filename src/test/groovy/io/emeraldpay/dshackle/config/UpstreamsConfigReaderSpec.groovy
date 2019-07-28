package io.emeraldpay.dshackle.config

import spock.lang.Specification

class UpstreamsConfigReaderSpec extends Specification {

    UpstreamsConfigReader reader = new UpstreamsConfigReader()

    def "Parse standard config"() {
        setup:
        def config = this.class.getClassLoader().getResourceAsStream("upstreams-basic.yaml")
        when:
        def act = reader.read(config)
        then:
        act != null
        act.version == "v1"
        with(act.defaultOptions) {
            size() == 1
            with(get(0)) {
                chains == ["ethereum"]
                options.minPeers == 3
                options.disableSyncing
            }
        }
        act.upstreams.size() == 2
        with(act.upstreams.get(0)) {
            id == "local"
            chain == "ethereum"
            provider == "geth"
            connection instanceof UpstreamsConfig.EthereumConnection
            with((UpstreamsConfig.EthereumConnection)connection) {
                rpc != null
                rpc.url == new URI("http://localhost:8545")
                ws != null
                ws.url == new URI("ws://localhost:8546")
            }
        }
        with(act.upstreams.get(1)) {
            id == "infura"
            chain == "ethereum"
            provider == "infura"
            connection instanceof UpstreamsConfig.EthereumConnection
            with((UpstreamsConfig.EthereumConnection)connection) {
                rpc.url == new URI("https://mainnet.infura.io/v3/fa28c968191849c1aff541ad1d8511f2")
                rpc.auth instanceof UpstreamsConfig.BasicAuth
                with((UpstreamsConfig.BasicAuth)rpc.auth) {
                    key == "4fc258fe41a68149c199ad8f281f2015"
                }
                ws == null
            }

        }
    }

    def "Parse ds config"() {
        setup:
        def config = this.class.getClassLoader().getResourceAsStream("upstreams-ds.yaml")
        when:
        def act = reader.read(config)
        then:
        act != null
        act.version == "v1"
        act.upstreams.size() == 1
        with(act.upstreams.get(0)) {
            id == "remote"
            provider == "dshackle"
            connection instanceof UpstreamsConfig.GrpcConnection
            with((UpstreamsConfig.GrpcConnection)connection) {
                host == "10.2.0.15"
                auth instanceof UpstreamsConfig.TlsAuth
                with((UpstreamsConfig.TlsAuth)auth) {
                    ca == "/etc/ca.myservice.com.crt"
                    certificate == "/etc/client1.myservice.com.crt"
                    key == "/etc/client1.myservice.com.key"
                }
            }
        }
    }
}