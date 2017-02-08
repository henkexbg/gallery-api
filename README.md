# Gallery-API
Headless REST application for serving images and videos. No UI is included - it's a pure REST interface allowing navigation of a file tree and accessing media files. The backend takes care of scaling images, either to fully custom sizes or predefined image formats. Videos are transcoded (with the help of external binaries), and can be retrieved in a streaming fashion via HTTP Range headers (utilized by HTML5 for instance when requesting videos)

# Purpose
To be able to easily make your own images and videos available without having to upload them to a 3rd-party. This webapp is up and running in a few minutes and can easily be deployed either to a home server or a virtual machine somewhere in some cloud. While it is possible to configure it in another way, this application is protected by default (with basic authentication). Different users can be set up who can access different media.
Focus was also put make it easy to deploy even for not super-tech-savvy people (it remains to be seen whether this goal was reached!), by for instance not requiring a database, but querying the filesystem in realtime.

# Optional UI
There is a separate project that adds a UI on top of the REST webapp, see https://github.com/henkexbg/gallery.

# Demo
https://mixedbag.se/gallery
Username: sample, password: samplepw

# Prerequisites
- Java 8
- A servlet container such as Apache Tomcat. Has been successfully tested with version 8 and 9.
- Maven (if building the webapp from source). Not required during runtime.

# Build (from source)
- Go to root directory of repo [REPO_ROOT].
- Run mvn clean package
- In [REPO_ROOT]/target/ you can now either take the war file or the directory named gallery-X.X.X-SNAPSHOT/.
- The war-file is essentially just a zipped version of the directory.

#Configuration
For convenience, the webapp root directory will be called [WEBAPP_HOME]

Edit [WEBAPP_HOME]/WEB-INF/classes/gallery.properties

This file contains a number of properties relevant to a specific environment. Each property is described in the file but a short rundown of the essential ones follows here.

gallery.resizeDir - states the directory this webapp will use to store resized images and converted videos.

gallery.users.propertiesFile - Points to the location of another properties file, which is used to configure the available users of the webapp and the roles of each user.

gallery.groupDirAuth.properties - Points to the location of another properties file which states which roles can access which paths.

# Optional Configuration
A recommendation would be to use SSL, either via a fronting web server such as HTTPD or by other means, especially since HTTP basic auth is used, but the setup of that is outside the scope of this webapp.

