import java.text.SimpleDateFormat

def call(body) {
    node('RemoteBuildPC') {        
        stage('test') {

            tz = TimeZone.getTimeZone("Asia/Taipei")
            def date = new Date()
            def sdf = new SimpleDateFormat("yyyyMMdd_HHmmss")
            def timestring = sdf.format(date)

            println timestring
        }
    }
}