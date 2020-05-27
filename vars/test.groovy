import java.text.SimpleDateFormat

def call(body) {
    node('RemoteBuildPC') {        
        stage('test') {

            use (groovy.time.TimeCategory) {
                println new Date()
                println 10.hours.from.now
            }
        }
    }
}