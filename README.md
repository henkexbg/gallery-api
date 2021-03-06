# Gallery-API
Headless REST application for serving images and videos. No UI is included - it's a pure REST interface allowing navigation of a file tree and accessing media files. The backend takes care of scaling images, either to fully custom sizes or predefined image formats. Videos are transcoded (with the help of external binaries), and can be retrieved in a streaming fashion via HTTP Range headers (utilized by HTML5 for instance when requesting videos).

# Purpose
To be able to easily make your own images and videos available without having to upload them to a 3rd-party. This webapp is up and running in a few minutes and can easily be deployed either to a home server or a virtual machine somewhere in some cloud. While it is possible to configure it in another way, this application is protected by default (with basic authentication). Different users can be set up who can access different media.
Focus was also put make it easy to deploy even for not super-tech-savvy people (it remains to be seen whether this goal was reached!), by for instance not requiring a database, but querying the filesystem in realtime.

There was also a clear intent with separation of concern in excluding any UI from this artifact - the services of this webapp can easily be consumed by any other application (see sample requests/responses below).

# Detailed Functionality
The application works by configuring:
- Users, along with their passwords and roles
- Directories that a certain role should have access to
- For video transcoding, the external binary needs to be specified
- Images are resized in Java code per default, and does not need an external binary. For those who wish, an implementation that uses ImageMagick also exists

The directories and sub-directories will then be made available by directly accessing the URL for that directory.

The exact configuration will be given below, along with JSON responses, but a high level example of the app functionality follows:

Let's assume:
- There is a directory called C:/some-base-dir/**image-dir**
- There is an image in this directory called **best-image-ever.jpg**
- A user is configured with role **COOL-IMAGES**
- The mapping for that role is: **ROLE_COOL-IMAGES.Best=C:/some-base-dir/image-dir**

The word Best here is the public path, i.e. the path through which the content of the directory can be accessed.
The user will now see **Best** as a possible option in the JSON response when calling [HOST]/gallery/service. They can now call [HOST]/gallery/service/Best, and all images, videos and sub-directories will be listed in the response.

All images will be listed with a **formatPath**, which links to each image. Part of the link is a placeholder called {imageFormat}. This can be replaced with any of the formats that is also part of the response. Standard configuration allows four formats:
- uhd
- qhd
- fullhd
- thumb

As part of the response when requesting Best the image **best-image-ever.jpg** will have the formatPath: /gallery/image/{imageFormat}/Best/best-image-ever.jpg

To retrieve the actual image, perform the replacement as mentioned above. For example, in order to get the fullhd version, the URL requested should be **/gallery/image/fullhd/Best/best-image-ever.jpg**

All images that have been resized and videos that have been transcoded are stored under a resized directory.


# Optional UI
There is a separate project that adds a UI on top of the REST webapp, see https://github.com/henkexbg/gallery.

# Demo
Demo not available at the moment. Please see examples of API use further down.

# Prerequisites
- Java 8
- A servlet container such as Apache Tomcat. Has been successfully tested with version 8 and 9.
- Maven (if building the webapp from source). Not required during runtime.

# Maven Artifact ID
- Group: com.github.henkexbg
- Artifact ID: gallery-api
- Latest release version: 0.5.3

# Download
The whole WAR file can be downloaded from Maven Central. Latest version can be found here:

https://search.maven.org/remotecontent?filepath=com/github/henkexbg/gallery-api/0.5.3/gallery-api-0.5.3.war

# Build From Source
- Go to root directory of repo [REPO_ROOT].
- Run mvn clean package
- In [REPO_ROOT]/target/ you can now either take the war file or the directory named gallery-api-X.X.X-SNAPSHOT/.
- The war-file is essentially just a zipped version of the directory.

# Configuration
For convenience, the webapp root directory will be called [WEBAPP_HOME]

Make a copy of [WEBAPP_HOME]/WEB-INF/classes/gallery.properties.sample and call it [WEBAPP_HOME]/WEB-INF/classes/gallery.properties.

This file contains a number of properties relevant to a specific environment. Each property is described in the file but a short rundown of the essential ones follows here.

gallery.resizeDir - states the directory this webapp will use to store resized images and converted videos.

gallery.users.propertiesFile - Points to the location of another properties file, which is used to configure the available users of the webapp and the roles of each user.

gallery.groupDirAuth.properties - Points to the location of another properties file which states which roles can access which paths.

# Optional Configuration
A recommendation would be to use SSL, either via a fronting web server such as HTTPD or by other means, especially since HTTP basic auth is used, but the setup of that is outside the scope of this webapp.


# Sample JSON Request/Response

## Request

https://HOST:PORT/gallery/service/sample

The /gallery part is the name of the web application which may differ depending on how app is deployed
The /service part is the path there all REST requests are directed.
Anything after /service (sample in this case) is a virtual path to a file - sample being a directory.
If one would call a URL with only the /service part and nothing after it, all root paths configured for that user will be listed.

## Response

The response will contains all information of what the sample directory contains:

````json
{  
   "comment":"Name of current dir",
   "currentPathDisplay":"sample",
   "comment":"Previous dir if any",
   "previousPath":null,
   "comment":"List of child directories of the current directory",
   "directories":{  
         "sample2":"/gallery/service/sample/sample2"
   },
   "comment":"Next comes a list of all images",
   "images":[  
      {  
         "comment":"freeSizePath is the URL template for a custom size image. Width and height will need to be replaced",
         "freeSizePath":"/gallery/customImage/{width}/{height}/sample/IMG_20150124_113749.jpg",
         "comment":"formatPath is the URL template for a specified image format",
         "formatPath":"/gallery/image/{imageFormat}/sample/IMG_20150124_113749.jpg",
         "contentType":"image/jpeg",
         "filename":"IMG_20150124_113749.jpg"
      },
      {  
         "freeSizePath":"/gallery/customImage/{width}/{height}/sample/IMG_2909.JPG",
         "formatPath":"/gallery/image/{imageFormat}/sample/IMG_2909.JPG",
         "contentType":"image/jpeg",
         "filename":"IMG_2909.JPG"
      }
   ],
   "comment":"Next, all videos",
   "videos":[  
      {  
         "comment":"freeSizePath will always be null for videos",
         "freeSizePath":null,
         "comment":"formatPath follows the same idea as that for images, though only conversionFormat needs to be replaced",
         "formatPath":"/gallery/video/{conversionFormat}/sample/MVI_2273.MP4",
         "contentType":"video/mp4",
         "filename":"MVI_2273.MP4"
      }
   ],
   "videoFormats":[  
      "COMPACT",
      "ORIGINAL"
   ],
   "comment":"Server config dictates whether custom image sizes are allowed, or only image formats",
   "allowCustomImageSizes":false, 
   "comment":"Image formats by codes, that also display the sizes they correspond to",
   "imageFormats":[  
      {  
         "code":"uhd",
         "width":3840,
         "height":2160
      },
      {  
         "code":"qhd",
         "width":2560,
         "height":1440
      },
      {  
         "code":"fullhd",
         "width":1920,
         "height":1080
      },
      {  
         "code":"thumb",
         "width":300,
         "height":300
      }
   ]
}

````
