pipeline {
    agent any

    environment {
        // ===== Gradle =====
        GRADLE_USER_HOME = "${WORKSPACE}/.gradle"

        // ===== SonarCloud =====
        SONAR_PROJECT_KEY = "GroomCloudTeam2_e_commerce_v2"
        SONAR_ORG         = "groomcloudteam2"
        SONAR_HOST_URL    = "https://sonarcloud.io"
    }

    options {
        timestamps()
    }

    stages {

        /* =========================
         * 0️⃣ Java / Gradle Check
         * ========================= */
        stage('Check Java Version') {
            steps {
                sh '''
                  java -version
                  ./gradlew -version
                '''
            }
        }

        /* =========================
         * 1️⃣ Checkout
         * ========================= */
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        /* =========================
         * 2️⃣ Unit & Slice Tests
         * ========================= */
        stage('Test') {
            steps {
                sh '''
                  ./gradlew clean test jacocoTestReport
                '''
            }
            post {
                always {
                    // 멀티 모듈 대응
                    junit '**/build/test-results/test/**/*.xml'
                    archiveArtifacts artifacts: '**/build/reports/jacoco/test/jacocoTestReport.xml'
                }
            }
        }
//
//         /* =========================
//          * 3️⃣ SonarCloud Analysis
//          * ========================= */
//         stage('SonarCloud Analysis') {
//             environment {
//                 SONAR_TOKEN = credentials('sonarcloud-token')
//             }
//             steps {
//                 withSonarQubeEnv('sonarcloud') {
//                     sh '''
//                       ./gradlew sonarqube \
//                         -Dsonar.projectKey=$SONAR_PROJECT_KEY \
//                         -Dsonar.organization=$SONAR_ORG \
//                         -Dsonar.host.url=$SONAR_HOST_URL \
//                         -Dsonar.token=$SONAR_TOKEN \
//                         -Dsonar.coverage.jacoco.xmlReportPaths=**/build/reports/jacoco/test/jacocoTestReport.xml
//                     '''
//                 }
//             }
//         }
//
//         /* =========================
//          * 4️⃣ Quality Gate
//          * ========================= */
//         stage('Quality Gate') {
//             steps {
//                 timeout(time: 3, unit: 'MINUTES') {
//                     waitForQualityGate abortPipeline: true
//                 }
//             }
//         }

        /* =========================
         * 5️⃣ Build JAR (All Modules)
         * ========================= */
        stage('Build') {
            steps {
                sh '''
                  ./gradlew build -x test
                '''
            }
        }
    }

    post {
        success {
            echo '✅ CI Pipeline Success (All JARs Built)'
        }
        failure {
            echo '❌ CI Pipeline Failed'
        }
        always {
            // 모든 모듈 jar 아카이빙 (선택)
            archiveArtifacts artifacts: '**/build/libs/*.jar', fingerprint: true
        }
    }
}
