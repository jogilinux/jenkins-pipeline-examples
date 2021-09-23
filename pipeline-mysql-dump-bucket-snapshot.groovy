import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import java.text.SimpleDateFormat

def volumeId = null
def volumeName = 'vistoria'
def now = new Date().format('YYYYMMdd')
def _do = 'https://api.digitalocean.com/v2'

node {
    withCredentials([usernamePassword(credentialsId: 'example-mysql', passwordVariable: 'PASS', usernameVariable: 'USER')]) {
       stage('Dump') {
           sh "mysqldump -h 10.0.0.1 -u ${USER} -p'${PASS}' example | gzip > example-${now}.sql.gz"
       }
    }
    withAWS(credentials: 'example-space', endpointUrl: 'https://sfo2.digitaloceanspaces.com', region: 'sfo2') {
        stage('Store') {
            try {
                s3Upload acl: 'Private', bucket: 'example', file: 'mysql/', text: ''
            } catch (ex) {
                print ex
            }
            s3Upload acl: 'Private', bucket: 'example', file: "example-${now}.sql.gz", path: "mysql/"
        }
        stage('Rotate') {
            def files = s3FindFiles bucket: 'example', onlyFiles: true, path: 'mysql/', glob: '*.sql.gz'
            if (files.size() > 7) {
                s3Delete bucket: 'example', path: 'mysql/' + files.first().path
            }
        }
    }
    withCredentials([usernamePassword(credentialsId: 'digital-ocean', passwordVariable: 'TOKEN', usernameVariable: 'USER')]) {
        def headers = [[name: 'Authorization', value: "Bearer ${TOKEN}"],[name: 'Content-Type', value : 'application/json']]
        stage('Find') {
            def response = httpRequest url: "${_do}/volumes?name=${volumeName}", customHeaders: headers
            def volumeData = new JsonSlurper().parseText(response.content)
            volumeId = volumeData.volumes[0].id
        }
        stage('Snapshot') {
           httpRequest url: "${_do}/volumes/${volumeId}/snapshots",
                httpMode: 'POST', 
                customHeaders: headers, 
                requestBody: JsonOutput.toJson([name: "vistoria-${now}"]), 
                validResponseCodes: '100:399,409,429'
        }
        stage('Rotate') {
            def response = httpRequest url: "${_do}/volumes/${volumeId}/snapshots", customHeaders: headers
            def snapshotData = new JsonSlurper().parseText(response.content)
            if(snapshotData.snapshots.size() > 1) {
                def oldest = null
                snapshotData.snapshots.each {
                    def d = new SimpleDateFormat("yyyy-MM-dd").parse(it.created_at)
                    if(!oldest || d < oldest['created_at'])
                        oldest = [id: it.id, created_at: d]
                }
                httpRequest url: "${_do}/snapshots/${oldest['id']}", httpMode: 'DELETE', customHeaders: headers
            }
        }
    }
        stage('Clean') {
        cleanWs()
    }
