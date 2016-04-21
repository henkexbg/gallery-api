# Gallery
Responsive web application for displaying your images and videos wherever you are. Images are resized for the screen size in question, and videos are converted to configurable formats. Consists of a web application written in Java 8 using Spring framework, and a front end written in Angular JS.

# Purpose
To be able to easily make your own images and videos available without having to upload them to a 3rd-party. This webapp is up and running in a few minutes and can easily be deployed either to a home server or a virtual machine somewhere in some cloud. While it is possible to configure it in another way, this application is protected by default (with basic authentication). Different users can be set up who can access different media.
Focus was also put make it easy to deploy even for not super-tech-savvy people (it remains to be seen whether this goal was reached!), by for instance not requiring a database.

# Demo
https://mixedbag.se/gallery
Username: sample, password: samplepw

# Prerequisites
- Java 8
- A servlet container such as Apache Tomcat. Has been successfully tested with version 8 and 9.
- Maven (if building the webapp from source). Not required during runtime.

# Build
- Go to root directory of repo [REPO_ROOT].
- Run mvn clean all.
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
A recommendation would be to use SSL, either via a fronting web server such as HTTPD or by other means, but the setup of that is outside the scope of this webapp.

# Known Issues
- Browser compatibility is most likely not great. Works in Chrome both on desktop and mobile. Know that Firefox has some issues, especially with fullscreen.
- Currently possible to uncheck video conversion mode.
- There is no lazy loading on either gallery or slideshow, works surprisingly well up to several hundred images in one directory though.
- Slideshow seems to load images in a slightly random order.
