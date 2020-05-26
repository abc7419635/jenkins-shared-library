def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    node {
        echo config.HW
        echo config.P1
        echo config.Custom
    }
}