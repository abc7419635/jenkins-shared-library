import java.text.SimpleDateFormat
import groovy.time.TimeCategory

def call(body) {
    node('RemoteBuildPC') {        
        stage('test') {

            now = new Date()
            use(TimeCategory) {
                ydate = now - 1.days
                println ydate
            }
        }
    }
}