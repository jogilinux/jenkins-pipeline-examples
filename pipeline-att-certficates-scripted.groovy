import groovy.json.JsonOutput

def server = '10.0.0.1'
def domains = [
    arplac:['controle.example.com.br',
        'controlediretoria.example.com.br',
        'controleqai.example.com.br']
]
def message = [
    personalizations: [[
        to: [
            [email: "nome1@example.com.br"],
            [email: "nome2@example.com.br"]
        ]
    ]],
    from: [email: "jenkins@example.com.br"],
    subject: "Jenkins - Falha Certbot",
    content: [[type: "text/plain", value: "Problemas ao atualizar os certificados em $JOB_NAME"]]
]

node {
    withCredentials([sshUserPrivateKey(credentialsId: 'jenkins-key', keyFileVariable: 'sshkey', usernameVariable: 'sshuser')]) {
        try {
            stage('Stop') {
                sh 'ssh -i ${sshkey} ${sshuser}' + "@${server} 'systemctl stop lighttpd'"
            }
            stage('Renew') {
                domains.each {
                    def certificates = it.value.join(',')
                    sh 'ssh -i ${sshkey} ${sshuser}' + "@${server} 'certbot certonly -d ${certificates} --standalone -n --agree-tos'"
                }
            }
        } catch (e) {
            withCredentials([usernamePassword(credentialsId: 'jenkins-sendgrid', passwordVariable: 'token', usernameVariable: 'user')]) {
                def headers = [[maskValue: false, name: 'Authorization', value: 'Bearer ${token}'], [maskValue: false, name: 'Content-Type', value: 'application/json']]
                httpRequest url: 'https://api.sendgrid.com/v3/mail/send',
                    customHeaders: headers,
                    httpMode: 'POST', 
                    requestBody: JsonOutput.toJson(message),
                    consoleLogResponseBody: true
            }
            throw e
        } finally {
            sh 'ssh -i ${sshkey} ${sshuser}' + "@${server} 'systemctl start lighttpd'"
        }
    }
}
