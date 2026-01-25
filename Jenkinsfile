pipeline {
    agent any

    environment {
        // ===== Gradle =====
        GRADLE_USER_HOME = "${WORKSPACE}/.gradle"

        // ===== Docker (나중에 사용을 위해 유지) =====
        DOCKER_REGISTRY = "boxty123"
        IMAGE_TAG = "${BUILD_NUMBER}-${GIT_COMMIT[0..7]}"

        // ===== SonarCloud (주석 처리) =====
        // SONAR_PROJECT_KEY = "GroomCloudTeam2_e_commerce_v2"
        // SONAR_ORG = "groomcloudteam2"
        // SONAR_HOST_URL = "https://sonarcloud.io"
    }

    options {
        timestamps()
    }

    stages {
        stage('1. Preparation') {
            steps {
                sh 'java -version && ./gradlew -version'
            }
        }

        stage('2. Checkout') {
            steps {
                checkout scm
            }
        }

        /* =========================
         * 3️⃣ 전체 모듈 빌드 (.jar 생성)
         * -x test: 일단 빌드 속도를 위해 테스트를 제외하고 싶다면 추가 (필요시 제거)
         * bootJar: Spring Boot 실행 가능한 JAR만 생성
         * ========================= */
        stage('3. Build All Jars') {
            steps {
                echo "--- Starting Multi-Module Build ---"
                // --parallel 옵션으로 common, user, order 등을 동시에 빌드
                sh './gradlew clean bootJar --parallel'
            }
            post {
                success {
                    echo "--- Archiving Artifacts ---"
                    // 빌드된 모든 서비스의 JAR 파일을 젠킨스 결과물로 저장
                    archiveArtifacts artifacts: 'service/*/build/libs/*.jar', followSymlinks: false
                }
            }
        }

        /* =========================
         * 4️⃣ SonarCloud & Quality Gate (주석 처리)
         * =========================
        stage('4. Static Analysis') {
            environment {
                SONAR_TOKEN = credentials('sonarcloud-token')
            }
            steps {
                withSonarQubeEnv('sonarcloud') {
                    sh "./gradlew sonarqube -Dsonar.token=$SONAR_TOKEN"
                }
                timeout(time: 5, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }
        */

        /* =========================
         * 5️⃣ Docker Build & Scan (주석 처리)
         * =========================
        stage('5. Microservices Dockerizing') {
            parallel {
                stage('Service: User') { steps { buildAndScan('user') } }
                stage('Service: Cart') { steps { buildAndScan('cart') } }
                stage('Service: Order') { steps { buildAndScan('order') } }
                stage('Service: Payment') { steps { buildAndScan('payment') } }
                stage('Service: Product') { steps { buildAndScan('product') } }
            }
        }
        */
    }

    post {
        success {
            echo '✅ 모든 모듈의 JAR 빌드가 성공적으로 완료되었습니다!'
        }
        failure {
            echo '❌ 빌드 실패!'
        }
    }
}

/**
 * 나중에 주석 풀 때 사용할 Docker 빌드 함수 (구조 유지)
 */
def buildAndScan(serviceName) {
    def fullImageName = "${DOCKER_REGISTRY}/msa-${serviceName}:${IMAGE_TAG}"
    def projectDir = "service/${serviceName}"
    script {
        sh "echo 'Building ${fullImageName} from ${projectDir}'"
        // sh "docker build -t ${fullImageName} ./${projectDir}"
        // sh "trivy image --severity HIGH,CRITICAL --exit-code 1 ${fullImageName}"
    }
}