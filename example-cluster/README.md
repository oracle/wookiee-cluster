Example Cluster Service
=======================

## Running Service Locally
If you want to see what it looks like to have a clustered Wookiee service running, 
then open this repo in IntelliJ Idea and do the following...

* Right click on pom.xml in example-cluster/ and click "Add as Maven Project"
    * This adds the example repo and its code as a module to your project
* Edit Run/Debug Configurations
* Hit the "+" and select Application
* Input the Following:
    * Main Class: com.webtrends.harness.app.HarnessService
    * VM Options: -Dconfig.file=src/main/resources/application.conf -Dlogback.configurationFile=src/main/resources/logback.xml
    * Working Directory: ${your path to ../wookiee-cluster/example-cluster}
    * Use Classpath of Module: example-cluster
* Press "OK"
