def mvnProfile    = 'dev'

pipeline {
// Initially run on any agent
   agent any
   environment {
//Configure Maven from the maven tooling in Jenkins
      def mvnHome = tool 'Default'
      PATH = "${mvnHome}/bin:${env.PATH}"
      
//Set some defaults
      def workspace = pwd()
      def mvnGoal    = 'install'
   }
   stages {
// If it is the master branch, version 0.3.0 and master on all the other branches
      stage('set-dev') {
         when {
           environment name: 'GIT_BRANCH', value: 'origin/master'
         }
         steps {
            script {
               mvnGoal       = 'deploy sonar:sonar'
            }
         }
      }
// If the staging branch,  then set as appropriate
      stage('set-test-preprod') {
         when {
           environment name: 'GIT_BRANCH', value: 'origin/staging'
         }
         steps {
            script {
               mvnGoal       = 'deploy'
               mvnProfile    = 'staging'
            }
         }
     }

// for debugging purposes
      stage('report') {
         steps {
            echo "Branch/Tag         : ${env.GIT_BRANCH}"
            echo "Workspace directory: ${workspace}"
            echo "Maven Goal         : ${mvnGoal}"
            echo "Maven profile      : ${mvnProfile}"
         }
      }
   
// Set up the workspace, clear the git directories and setup the manve settings.xml files
      stage('prep-workspace') { 
         steps {
            configFileProvider([configFile(fileId: '86dde059-684b-4300-b595-64e83c2dd217', targetLocation: 'settings.xml')]) {
            }
            dir('repository/dev.galasa') {
               deleteDir()
            }
            dir('repository/dev/galasa') {
               deleteDir()
            }
         }
      }
      
// Build the wrapping repository
      stage('maven') {
         steps {
            withCredentials([string(credentialsId: 'galasa-gpg', variable: 'GPG')]) {
            withSonarQubeEnv('GalasaSonarQube') {
               dir('galasa-maven-plugin') {
                  sh "mvn --settings ${workspace}/settings.xml -Dgpg.skip=false -Dgpg.passphrase=$GPG -Dmaven.repo.local=${workspace}/repository -P ${mvnProfile} -B -e -fae ${mvnGoal}"
               }
            }
            }
         }
      }
   }
   post {
       // triggered when red sign
       failure {
           slackSend (channel: '#project-galasa-devs', color: '#FF0000', message: "FAILED: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]' (${env.BUILD_URL})")
       }
    }
}
