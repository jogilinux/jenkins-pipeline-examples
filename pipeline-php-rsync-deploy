server = '10.0.0.1'

pipeline {
    agent any
    stages {
        stage('Deploy') {
            steps {
                withCredentials([sshUserPrivateKey(credentialsId: 'jenkins-key', keyFileVariable: 'key', usernameVariable: 'user')]) {
                    sh 'rm -rf .git infra k8s'
                    sh "ssh -i $key $user@$server 'mkdir -p /var/www/hectorvido'"
                    sh "rsync -i $key -a . $user@$server:/var/www/hectorvido/"
                    sh "ssh -i $key $user@$server 'chown -R www-data: /var/www/hectorvido'"
                }
            }
        }
        stage('Composer') { 
            steps {
                withCredentials([sshUserPrivateKey(credentialsId: 'jenkins-key', keyFileVariable: 'key', usernameVariable: 'user')]) {
                    sh """ssh -i $key $user@$server '\
                    cd /var/www/hectorvido && \
                    su -s /bin/sh -c "composer update" www-data'"""
                }
            }
        }
    }
}
