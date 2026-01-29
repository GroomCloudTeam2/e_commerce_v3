@Library('jenkins-shared-lib@main') _

pipeline {
    agent any

    environment {
        GRADLE_USER_HOME = "/var/jenkins_home/.gradle"

        AWS_REGION     = "ap-northeast-2"
        AWS_ACCOUNT_ID = "900808296075"
        ECR_REGISTRY   = "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"

        IMAGE_TAG = "${BUILD_NUMBER}-${GIT_COMMIT[0..7]}"
        SLACK_CHANNEL = "#jenkins-alerts"

        ECS_CLUSTER = "courm-cluster-prod"
    }

    options {
        timeout(time: 30, unit: 'MINUTES')
        timestamps()
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    stages {

        /* ================= CI ================= */

        stage('CI') {
            stages {

                stage('Detect Changes') {
                    steps {
                        script {
                            CHANGED_SERVICES = getChangedServices()
                            echo "🔍 Changed services: ${CHANGED_SERVICES}"
                        }
                    }
                }

                stage('Gradle Build') {
                    when {
                        expression { CHANGED_SERVICES && !CHANGED_SERVICES.isEmpty() }
                    }
                    steps {
                        sh """
                          ./gradlew \
                          ${CHANGED_SERVICES.collect { ":service:${it}:bootJar" }.join(' ')} \
                          --no-daemon \
                          --parallel \
                          --build-cache \
                          --configuration-cache
                        """
                    }
                }

                stage('Unit Test') {
                    when {
                        expression { CHANGED_SERVICES && !CHANGED_SERVICES.isEmpty() }
                    }
                    steps {
                        sh """
                          ./gradlew \
                          ${CHANGED_SERVICES.collect { ":service:${it}:test" }.join(' ')} \
                          --no-daemon
                        """
                    }
                }

                stage('Docker Build (parallel)') {
                    when {
                        expression { CHANGED_SERVICES && !CHANGED_SERVICES.isEmpty() }
                    }
                    steps {
                        script {
                            def tasks = [:]
                            CHANGED_SERVICES.each { svc ->
                                tasks[svc] = {
                                    buildDockerImage(svc)
                                }
                            }
                            parallel tasks
                        }
                    }
                }

        /* ================= CD ================= */

        stage('CD') {
            when {
                allOf {
                    branch 'main'
                    expression { CHANGED_SERVICES && !CHANGED_SERVICES.isEmpty() }
                }
            }

            stages {

                stage('ECR Login') {
                    steps {
                        sh '''
                            aws ecr get-login-password --region $AWS_REGION \
                            | docker login --username AWS --password-stdin $ECR_REGISTRY
                        '''
                    }
                }

                stage('Push Images') {
                    steps {
                        script {
                            parallel CHANGED_SERVICES.collectEntries { svc ->
                                [(svc): { pushImage(svc) }]
                            }
                        }
                    }
                }

                stage('Deploy ECS (Update Service)') {
                    steps {
                        script {
                            parallel CHANGED_SERVICES.collectEntries { svc ->
                                [(svc): { deployService(serviceName: svc) }]
                            }
                        }
                    }
                }
            }
        }
    }
    post {
      success {
        slackNotify(status: 'SUCCESS', channel: SLACK_CHANNEL, services: CHANGED_SERVICES)
      }
      failure {
        slackNotify(status: 'FAILURE', channel: SLACK_CHANNEL, services: CHANGED_SERVICES)
      }
      always {
        archiveArtifacts artifacts: 'trivy-reports/*.json', allowEmptyArchive: true
      }
    }
}

