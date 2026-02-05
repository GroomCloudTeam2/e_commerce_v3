@Library('jenkins-shared-lib@local-demo') _

def CHANGED_SERVICES = []

// ✅ 서비스별 ECR repo명 규칙 (필요하면 여기만 수정)
// 예) goorm-user, goorm-cart ...
def repoFor = { String svc ->
    return "${env.ECR_REGISTRY}/goorm-${svc}"
}

pipeline {
    agent none

    environment {
        AWS_REGION         = "ap-northeast-2"
        AWS_ACCOUNT_ID     = "900808296075"
        ECR_REGISTRY       = "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"

        GITOPS_REPO_URL    = "https://github.com/GroomCloudTeam2/courm-helm.git"
        GITOPS_DIR         = "courm-helm"
        GITOPS_BRANCH      = "jib_test"
        GITOPS_VALUES_BASE = "services"

        SLACK_CHANNEL      = "#jenkins-alerts"
    }

    options {
        timeout(time: 30, unit: 'MINUTES')
        timestamps()
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    stages {

        stage('Init') {
            agent {
                kubernetes {
                    inheritFrom 'gradle-agent'
                    defaultContainer 'gradle'
                }
            }
            steps {
                script {
                    // ✅ pod 기반 dynamic agent에서는 워크스페이스 캐시가 안전
                    env.GRADLE_USER_HOME = "${env.WORKSPACE}/.gradle"

                    // ✅ GIT_COMMIT이 비어도 터지지 않게
                    def shortSha = (env.GIT_COMMIT ?: "unknown").take(8)
                    env.IMAGE_TAG = "${env.BUILD_NUMBER}-${shortSha}"

                    echo "GRADLE_USER_HOME=${env.GRADLE_USER_HOME}"
                    echo "IMAGE_TAG=${env.IMAGE_TAG}"
                }
            }
        }

        stage('Detect Changes') {
            agent {
                kubernetes {
                    inheritFrom 'gradle-agent'
                    defaultContainer 'gradle'
                }
            }
            steps {
                script {
                    CHANGED_SERVICES = getChangedServices()
                    echo "Changed services: ${CHANGED_SERVICES}"

                    if (!CHANGED_SERVICES || CHANGED_SERVICES.isEmpty()) {
                        echo "No changed services. Pipeline will skip build/push/update stages."
                    }
                }
            }
        }

        stage('Gradle Test') {
            when { expression { CHANGED_SERVICES && !CHANGED_SERVICES.isEmpty() } }
            agent {
                kubernetes {
                    inheritFrom 'gradle-agent'
                    defaultContainer 'gradle'
                }
            }
            steps {
                script {
                    def testTasks = CHANGED_SERVICES
                        .collect { ":service:${it}:test :service:${it}:jacocoTestReport" }
                        .join(' ')
                    sh "./gradlew ${testTasks} -DexcludeTags=Integration --no-daemon --parallel"
                }
            }
            post {
                always {
                    junit '**/build/test-results/test/*.xml'
                }
            }
        }

        // ✅ bootJar 단계 제거 (Jib와 중복)
        stage('Jib Build & Push') {
            when {
                allOf {
                    branch 'main'
                    expression { CHANGED_SERVICES && !CHANGED_SERVICES.isEmpty() }
                }
            }
            agent {
                kubernetes {
                    inheritFrom 'gradle-agent'
                    defaultContainer 'gradle'
                }
            }
            steps {
                script {
                    // (선택) AWS 권한 확인용 - 문제 생길 때만 켜도 됨
                    sh "aws sts get-caller-identity || true"

                    parallel CHANGED_SERVICES.collectEntries { svc ->
                        [(svc): {
                            def image = "${repoFor(svc)}:${env.IMAGE_TAG}"
                            echo "Jib push -> ${image}"

                            sh """
                                set -e
                                set +x
                                ECR_PW=\$(aws ecr get-login-password --region ${AWS_REGION})

                                ./gradlew :service:${svc}:jib --no-daemon \\
                                  -Djib.to.image=${image} \\
                                  -Djib.to.auth.username=AWS \\
                                  -Djib.to.auth.password=\$ECR_PW
                            """
                        }]
                    }
                }
            }
        }

        stage('Update GitOps Repo') {
            when {
                allOf {
                    branch 'main'
                    expression { CHANGED_SERVICES && !CHANGED_SERVICES.isEmpty() }
                }
            }
            agent {
                kubernetes {
                    inheritFrom 'gradle-agent'
                    defaultContainer 'gradle'
                }
            }
            steps {
                withCredentials([
                    string(credentialsId: 'github-pat', variable: 'GIT_TOKEN')
                ]) {
                    sh """
                        set -e
                        rm -rf ${GITOPS_DIR}
                        git clone https://x-access-token:${GIT_TOKEN}@github.com/GroomCloudTeam2/courm-helm.git ${GITOPS_DIR}
                        cd ${GITOPS_DIR}
                        git checkout ${GITOPS_BRANCH}
                    """

                    script {
                        CHANGED_SERVICES.each { svc ->
                            sh """
                                set -e
                                cd ${GITOPS_DIR}
                                sed -i 's|tag:.*|tag: "${IMAGE_TAG}"|' ${GITOPS_VALUES_BASE}/${svc}-service/values.yaml
                            """
                        }
                    }

                    sh """
                        set -e
                        cd ${GITOPS_DIR}
                        git config user.email "hyunho3445@gmail.com"
                        git config user.name "yyytgf123"
                        git add .
                        git commit -m "Update services [${CHANGED_SERVICES.join(', ')}] to ${IMAGE_TAG}" || echo "No changes"
                        git push origin ${GITOPS_BRANCH}
                    """
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
