pipeline {
// Initially run on any agent
   agent any
   environment {
//Configure Maven from the maven tooling in Jenkins
      def mvnHome = tool 'Default'
      PATH = "${mvnHome}/bin:${env.PATH}"
      
//Set some defaults
      def workspace = pwd()
   }
   stages {  
// Set up the workspace, clear the git directories and setup the manen settings.xml files
      stage('prep-workspace') { 
         steps {
            configFileProvider([configFile(fileId: "${env.MAVEN_SETTINGS}", targetLocation: 'settings.xml')]) {
            }
            dir('repository/dev.galasa') {
               deleteDir()
            }
            dir('repository/dev/galasa') {
               deleteDir()
            }
         }
      }
      
// Build the maven repository
      stage('maven') {
         steps {
            withCredentials([string(credentialsId: "${env.GPG_CREDENTIALS}", variable: 'GPG')]) {
               withSonarQubeEnv('GalasaSonarQube') {
                  withFolderProperties {
                     dir('galasa-maven-plugin') {
                        sh "mvn --settings ${workspace}/settings.xml -Dgpg.skip=${env.GPG_SKIP} -Dgpg.passphrase=$GPG -Dmaven.repo.local=${workspace}/repository -P ${env.MAVEN_PROFILE} -B -e -fae ${env.MAVEN_GOAL}"
                     }
                  }
               }
            }
         }
      }
   }
   post {
       // triggered when red sign
       failure {
           slackSend (channel: '#galasa-devs', color: '#FF0000', message: "FAILED: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]' (${env.BUILD_URL})")
       }
    }
}
