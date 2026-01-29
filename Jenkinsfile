@Library('jenkins-shared-lib') _

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

//                 stage('Image Scan (Trivy)') {
//                     when {
//                         expression { CHANGED_SERVICES && !CHANGED_SERVICES.isEmpty() }
//                     }
//                     steps {
//                         script {
//                             // 리포트 디렉토리 생성
//                             sh 'mkdir -p trivy-reports'
//
//                             parallel CHANGED_SERVICES.collectEntries { svc ->
//                                 [(svc): {
//                                     def imageName = "${ECR_REGISTRY}/goorm-${svc}:${IMAGE_TAG}"
//
//                                     echo "🔍 Scanning image: ${imageName}"
//
//                                     // 콘솔 출력용 (테이블)
//                                     sh """
//                                         docker run --rm \
//                                             -v /var/run/docker.sock:/var/run/docker.sock \
//                                             aquasec/trivy:latest image \
//                                             --severity HIGH,CRITICAL \
//                                             --exit-code 0 \
//                                             --no-progress \
//                                             ${imageName}
//                                     """
//
//                                     // JSON 리포트 저장
//                                     sh """
//                                         docker run --rm \
//                                             -v /var/run/docker.sock:/var/run/docker.sock \
//                                             -v ${WORKSPACE}/trivy-reports:/reports \
//                                             aquasec/trivy:latest image \
//                                             --severity HIGH,CRITICAL \
//                                             --exit-code 0 \
//                                             --format json \
//                                             -o /reports/${svc}-report.json \
//                                             ${imageName}
//                                     """
//
//                                     echo "Scan complete for ${svc}"
//                                 }]
//                             }
//                         }
//                     }
//                 }
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
            slackSend(
                channel: SLACK_CHANNEL,
                message: "성공\n브랜치: ${BRANCH_NAME ?: 'unknown'}\n서비스: ${CHANGED_SERVICES?.join(', ') ?: '없음'}"
            )
        }
        failure {
            slackSend(
                channel: SLACK_CHANNEL,
                message: "실패\n브랜치: ${BRANCH_NAME ?: 'unknown'}"
            )
        }
        always {
            // Trivy 리포트 아카이브 (Jenkins에서 다운로드 가능)
            archiveArtifacts artifacts: 'trivy-reports/*.json', allowEmptyArchive: true
        }
    }
}
