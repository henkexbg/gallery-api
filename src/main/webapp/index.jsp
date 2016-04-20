<!DOCTYPE html>
<html ng-app="gallery" lang="en">
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no" />
<title>Gallery</title>
<link rel="icon" href="static/img/favicon.ico" type="image/x-icon" />
<link rel="icon" sizes="192x192" href="static/img/icon-high-res.png" />
<link rel="stylesheet" href="static/css/bootstrap.min.css" />
<link rel="stylesheet" href="static/css/styles.css" />
<script type="text/javascript">
    /*<![CDATA[*/
    var contextPath = "${pageContext.request.contextPath}";
    /*]]>*/
</script>
</head>
<body ng-controller="GalleryController">

    <uib-tabset active="active">
        <uib-tab index="0" heading="NAVIGATION">
            <div class="btn-group" uib-dropdown is-open="status.isopen">
                <ul class="list-group">
                    <li class="list-group-item"><a href="#" ng-click="getListing()">Home</a></li>
                    <li class="list-group-item disabled" ng-if="currentPathDisplay">{{currentPathDisplay}}</li>
                    <li class="list-group-item" ng-if="previousPath"><a class="" href="#" ng-click="getListing(previousPath)"><span
                            class="glyphicon glyphicon-menu-up"></span> Previous</a></li>
                    <li class="list-group-item" ng-repeat="(key, value) in directories"><a href="#" ng-click="getListing(value)">{{key}}</a></li>
                </ul>
            </div>
        </uib-tab>
        <uib-tab index="1" heading="{{getGalleryTabTitle()}}">
            <div ng-show="!showSlides()" class="row">
                <div class="col-xs-4 col-sm-4 col-md-4 col-lg-2" ng-repeat="oneImage in images">
                    <a href="#" class="thumbnail"> <img ng-src="{{oneImage.thumbnailPath}}" alt="..." ng-click="toggleSlideshow($index)">
                    </a>
                </div>
            </div>
    
            <div id="fullScreenImage" ng-show="showSlides()">
                <uib-carousel id="carousel" interval="carouselInterval" no-wrap="noWrapSlides" active="currentIndex" ng-if="showSlides()">
                    <uib-slide ng-repeat="slide in images" index="$index">
                        <img ng-src="{{getFullScreenImageUrl(slide)}}" style="margin: auto;" ng-click="toggleSlideshow(0)">
                    </uib-slide>
                </uib-carousel>
            </div>
        </uib-tab>
        <uib-tab index="2" heading="{{getVideoTabTitle()}}">
            <div class="btn-group" uib-dropdown is-open="status.isopen">
                <ul class="list-group">
                    <li class="list-group-item" ng-repeat="video in videos">
                        <a href="#" ng-click="toggleVideo($index)">{{video.filename}}</a>
                        <video ng-if="shouldShowVideo($index)" width="320" height="240" style="max-width:100%" preload="none" controls>
                            <source ng-src="{{getVideoUrl(video)}}" type="{{video.contentType}}" />
                        </video>
                    </li>
                </ul>
            </div>
        </uib-tab>
        <uib-tab index="3" heading="SETTINGS">
            <h3>Video Quality</h3>
            <div class="btn-group">
                <label class="btn btn-primary" ng-model="activeVideoFormat.id" ng-repeat="videoFormat in videoFormats" uib-btn-radio="videoFormat" uncheckable>{{videoFormat}}</label>
            </div>
        </uib-tab>
   
        <script src="static/js/angular-1.5.2.js"></script>
        <script src="static/js/angular-animate-1.5.3.js"></script>
        <script src="static/js/angular-touch.min-1.5.3.js"></script>
        <script src="static/js/ui-bootstrap-tpls-1.2.5.js"></script>
        <script src="static/js/angular-fullscreen.js"></script>
        <script src="static/js/app.js"></script>
</body>
</html>
