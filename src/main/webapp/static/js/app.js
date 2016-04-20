var app = angular.module('gallery', [ 'ngAnimate', 'ui.bootstrap', 'FBAngular', 'ngTouch' ]);

app.controller('GalleryController', function($scope, $http, $timeout, $window, Fullscreen) {

    $scope.carouselInterval = 0;
    $scope.noWrapSlides = false;
    $scope.active = true;
    $scope.images = [];
    $scope.videos = [];
    $scope.videoFormats = [];
    $scope.activeVideoFormat = {};
    $scope.activeVideoFormat.id = null;
    $scope.currentIndex = 1;
    $scope.previousPath = null;
    $scope.currentPathDisplay = null;
    $scope.currentVideoIndex = -1;

    $scope.getListing = function(newUrl) {
        var url;
        if (newUrl != null) {
            url = newUrl;
        } else {
            url = contextPath + "/service";
        }
        $http.get(url).success(function(data, status, headers, config) {
            $scope.directories = data.directories;
            $scope.images = data.images;
            $scope.videos = data.videos;
            $scope.previousPath = data.previousPath;
            $scope.currentPathDisplay = data.currentPathDisplay;
            $scope.videoFormats = data.videoFormats;
            if ($scope.activeVideoFormat.id == null && $scope.videoFormats.length > 0) {
                $scope.activeVideoFormat.id = $scope.videoFormats[0];
            }
        }).error(function(data, status, headers, config) {
            alert("Error retrieving image listing!");
        });
    }

    $scope.toggleSlideshow = function(index) {
        $scope.goFullscreen();
        $scope.currentIndex = index;
    }

    $scope.showSlides = function() {
        return !!Fullscreen.isEnabled();
    }

    $scope.goFullscreen = function() {
        if (Fullscreen.isEnabled()) {
            Fullscreen.cancel();
        } else {
            console.log("Enabling fullscreen");
            // Fullscreen.all();
            Fullscreen.enable(document.getElementById('fullScreenImage'));
        }
    };

    $scope.getFullScreenImageUrl = function(slide) {
        var ratio = $window.devicePixelRatio || 1;
        var width = Math.round($window.screen.width * ratio);
        var height = Math.round($window.screen.height * ratio);
        if (slide.mediaPath != null) {
            return $scope.getCustomImageUrl(slide, width, height);
        }
        return "";
    }

    $scope.getCustomImageUrl = function(slide, width, height) {
        if (slide.mediaPath != null) {
            var url = slide.mediaPath.replace("{width}", width).replace("{height}", height);
            return url;
        }
        return "";
    }

    $scope.shouldShowVideo = function(index) {
        return $scope.currentVideoIndex == index;
    }

    $scope.toggleVideo = function(index) {
        if ($scope.currentVideoIndex == index) {
            $scope.currentVideoIndex = -1;
        } else {
            $scope.currentVideoIndex = index;
        }
    }

    $scope.getVideoUrl = function(video) {
        if (video.mediaPath != null) {
            var url = video.mediaPath.replace("{conversionFormat}", $scope.activeVideoFormat.id);
            return url;
        }
        return "";
    }

    $scope.getGalleryTabTitle = function() {
        return "GALLERY" + (($scope.images != null && $scope.images.length > 0) ? " (" + $scope.images.length + ")" : "");
    }

    $scope.getVideoTabTitle = function() {
        return "VIDEO" + (($scope.videos != null && $scope.videos.length > 0) ? " (" + $scope.videos.length + ")" : "");
    }

    $scope.getListing();

});
