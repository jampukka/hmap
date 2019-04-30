# hmap

A tool to create Cesium Heightmap tiles from a gridded elevation model served by a WCS service. Developed for NLS Finland.

## generating tiles
First build the project with maven to create hmap.jar, then running is:
```
java -jar hmap.jar $wcs_endpoint $coverage_id $dir $wgs84_lon1,lat1,lon2,lat2 $maxZoom
```
For example, small area around Helsinki using NLS Finland Open BETA WCS service
(all examples here will use /data/hmap as the base directory for the files, feel free to change it):
```
java -jar hmap.jar https://beta-karttakuva.maanmittauslaitos.fi/wcs/service/ows korkeusmalli__korkeusmalli /data/hmap/ 24.82,60.125,25.04,60.25 14
```
The smaller the extent the faster the process completes. The example should complete in a minute or so. Max zoom 13-14 is a good value for `korkeusmalli__korkeusmalli` (grid size 2m).

## metadata file
Create a metadata file /data/hmap/layer.json with the following contents:
```
{
  "tilejson": "2.1.0",
  "format": "heightmap-1.0",
  "version": "1.0.0",
  "scheme": "tms",
  "tiles": ["{z}/{x}/{y}.terrain"]
}
```

## serving the files over http(s)
We happen to use httpd to serve the files but you can use whatever you want. Here's a snippet of httpd config that allows CORS and lets the client know that the .terrain files are (always) gzipped:
```
<VirtualHost *:80>
        DocumentRoot "/data/hmap"
        ServerName my.server.com

<Files "*.json">
Header set Access-Control-Allow-Origin "*"
</Files>
<Files "*.terrain">
Header set Access-Control-Allow-Origin "*"
Header set Content-Type "application/octet-stream"
Header set Content-Encoding "gzip"
</Files>

<Directory "/data/hmap">
    AllowOverride None
    # Allow open access:
    Require all granted
</Directory>
</VirtualHost>
```
