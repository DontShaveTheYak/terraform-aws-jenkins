#!groovy

import jenkins.model.*
import groovy.json.JsonSlurper
import com.cloudbees.plugins.credentials.*
import com.cloudbees.plugins.credentials.domains.*
import org.jenkinsci.plugins.plaincredentials.impl.*

def sout = new StringBuilder()
def serr = new StringBuilder()
String ssmPath = '/IT/DevOps/Jenkins/Secrets/'
def store = SystemCredentialsProvider.instance.store

// Helper function to get json parsed metadata
def getJson(string){
    def jsonSlurper = new JsonSlurper()
    Map json_values = jsonSlurper.parseText(string)
    if ( json_values == null ) {
        return ""
    } else {
        return json_values
    }
}
println 'Calling get-parameters-by-path.'
def proc = "/var/jenkins_home/.local/bin/aws --region us-east-1 ssm get-parameters-by-path --path ${ssmPath} --with-decryption".execute()

proc.consumeProcessOutput(sout, serr)
proc.waitForOrKill(1000)

if(proc.exitValue()){
  println 'Command failed'
  println serr
}else{
  println 'Creating Map from command output.'
  Map jsonResults = getJson(sout.toString())
  println 'Stripping path from parameter name.'
  Map<String,String> parameters = jsonResults.Parameters.collectEntries{[(it.Name.minus("${ssmPath}")): it.Value]}
  println 'Creating list of credentials from Map of parameters.'
  List creds = parameters.collect{it ->
    (Credentials) new StringCredentialsImpl(
      CredentialsScope.GLOBAL,
      it.key,
      "${it.key}: Synced at boot with AWS SSM.",
      hudson.util.Secret.fromString(it.value)
    )
  }
  
  creds.each {
    println "Adding ${it.getId()} to Credentials store."
    store.addCredentials(Domain.global(), it)
  }
}
