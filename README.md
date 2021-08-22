# Gallery-API
Headless REST application for serving images and videos. No UI is included - it's a pure REST interface allowing navigation of a file tree and accessing media files. The backend takes care of scaling images, either to fully custom sizes or predefined image formats. Videos are transcoded (with the help of external binaries), and can be retrieved in a streaming fashion via HTTP Range headers. All access is behind authentication, and different users and roles can be set up.

# Purpose
To be able to easily make your images and videos available to yourself and share with friends and family without having to upload them to a 3rd-party. This webapp is up and running in a few minutes and can easily be deployed either to a home server or a virtual machine somewhere in some cloud. This application is protected by default with basic authentication. Different users can be set up who can access different media.

There was also a clear intent with separation of concern in excluding any UI from this artifact - the services of this webapp can easily be consumed by any other application (see sample requests/responses below).

# Optional UI
There is a separate project that adds a UI on top of the REST webapp, see https://github.com/henkexbg/gallery.

# Features
- REST API for browsing directories
- Serves scaled images and transcoded videos
- Requires authentication and validates that every single request is authenticated and authorized to view the requested content
- Packaged as a simple Spring boot application that requires only Java - no database required
- Automatically serves newly added content
- Images are scaled ad-hoc
- Transcoded videos are generated via a job or ad-hoc
- A video blacklist exists to ensure videos that fail to transcode keep hogging resources forever
- Users are configured server-side. There is no registration

# How It Works
The application works by configuring:
- Users, along with their passwords and roles
- Directories that a certain role should have access to
- For video transcoding, the external binary needs to be specified (such as ffmpeg)
- Images are resized in Java code per default, and does not need an external binary. For those who wish, an implementation that uses ImageMagick also exists

The directories and sub-directories will then be made available by directly accessing the URL for that directory. The URL may differ between users as it depends on the role configuration, see below.

The exact configuration will be given further down along with JSON responses, but a high level example of the app functionality follows:

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


# Demo
Demo not available at the moment. Please see examples of API use further down.

# Prerequisites
- Java 14
- Maven (if building the webapp from source). Not required during runtime.

# Maven Artifact ID
- Group: com.github.henkexbg
- Artifact ID: gallery-api
- Latest release version: 0.8.0

# Download
The whole JAR file can be downloaded from Maven Central. Latest version can be found here:

https://search.maven.org/remotecontent?filepath=com/github/henkexbg/gallery-api/0.8.0/gallery-api-0.8.0.jar

# Build From Source
- Go to root directory of repo [REPO_ROOT].
- Run mvn clean package
- In [REPO_ROOT]/target/ you can now find the JAR file called gallery-api.jar (or gallery-api-X.X.X-SNAPSHOT.jar for snapshot versions).

# Configuration
The application uses standard Spring Boot conventions for configuration. That means, the application.properties file can be placed either next to the JAR file, or in a directory called config next to the JAR file. Sample configuration of application.properties as well as two other properties files can be found here: https://github.com/henkexbg/gallery-api/tree/master/src/main/resources/sample_config.

## application.properties
While there are many properties that can be changed, the required ones are the following:

| Property Name  | Description  |
| ------------- | ------------- |
| gallery.resizeDir | States the directory this webapp will use to store resized images and converted videos. |
| gallery.users.propertiesFile | Points to the location of another properties file, which is used to configure the available users of the webapp and the roles of each user. See the sample file for reference. |
| gallery.groupDirAuth.properties | Points to the location of another properties file which states which roles can access which paths. See the sample file for reference. |
| gallery.videoConversion.binary | This should point to the binary used for video conversion, for instance avconv or ffmpeg. |
| gallery.videoConversion.blacklistedVideosFile | The application logs all videos that could not successfully be converted. Once on the list, no further attempt will be made to convert that video. |

# Optional Configuration
A strong recommendation would be to use SSL, either via a fronting web server such as HTTPD or by other means, especially since HTTP basic auth is used, but the setup of that is outside the scope of this webapp.

# Run

The program can be run by calling

````shell
java -jar gallery-api.jar
````
There are multiple ways to run this as a background process, all of which depend on the operating system used. Google is your friend :) .

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
   "comment":"List of child directories of the current directory",
   "directories":{  
         "sample2":"/gallery/service/sample/sample2"
   },
   "comment":"Next comes a list of all images and videos",
   "media":[  
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
      },
      {  
         "comment":"freeSizePath works as for images, and gives a still image of the video",
         "freeSizePath":null,
         "comment":"formatPath follows the same idea as that for images and gives a still image of the video",
         "formatPath":"/gallery/image/{imageFormat}/sample/MVI_2273.MP4",
         "comment":"Gives the video URL, though only conversionFormat needs to be replaced",
         "videoPath":"/gallery/video/{conversionFormat}/sample/MVI_2273.MP4",
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
   "comment":"if separateImagesAndVideos is true, images will be returned in the images attribute, and videos in the videos attribute"
   "separateImagesAndVideos":false,
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
