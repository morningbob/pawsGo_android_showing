# Walking Dogs

&nbsp;

## Walking Dogs Android Mobile App

&nbsp;
&nbsp;

### Walking dogs is an app for dog owners to share their favorite dog walk routes to the other dog owners.  The users can also get suggested routes by city, which are shared by the other users.

&nbsp;

### The app requires users to be authenticated by an email and a password.  The app uses Firebase Authentication to validate users.  

&nbsp;

### The app implements Google map to show the routes and locations.  Users can add markers to indicate a route.  The app posts requests to Google map API, Directions API, Geocoding API and Places API to get required information.

### The users can get recommended routes from a location they choose.  Individual routes will be shown upon click.  

&nbsp;

&nbsp;

<img src=".\shareRoutes_images\01_shareRoutes.jpg" alt="application home screenshot" style="width:250px; margin-left: auto; margin-right: auto; display: block;" />

&nbsp;
<center> Login screen </center>
&nbsp;

<img src=".\shareRoutes_images\04_shareRoutes.jpg" alt="create account page screenshot" style="width:250px; margin-left: auto; margin-right: auto; display: block;" />

&nbsp;
<center> Create Account screen </center>
&nbsp;

<img src=".\shareRoutes_images\03_shareRoutes.jpg" alt="forgot password screenshot" style="width:250px; margin-left: auto; margin-right: auto; display: block;" />

&nbsp;
<center> Send reset password email </center>
&nbsp;

<img src=".\shareRoutes_images\05_shareRoutes.jpg" alt="application home screenshot" style="width:250px; margin-left: auto; margin-right: auto; display: block;" />

&nbsp;
<center> Home Screen </center>
&nbsp;

<img src=".\shareRoutes_images\06_shareRoutes.jpg" alt="create a route screenshot" style="width:250px; margin-left: auto; margin-right: auto; display: block;" />

&nbsp;
<center> Creating a route </center>
&nbsp;

<img src=".\shareRoutes_images\07_shareRoutes.jpg" alt="sharing a route screenshot" style="width:250px; margin-left: auto; margin-right: auto; display: block;" />

&nbsp;
<center> Sharing a route </center>
&nbsp;

<img src=".\shareRoutes_images\09_shareRoutes.jpg" alt="list of suggested routes screenshot" style="width:250px; margin-left: auto; margin-right: auto; display: block;" />

&nbsp;
<center> showing recommended routes </center>
&nbsp;

<img src=".\shareRoutes_images\10_shareRoutes.jpg" alt="user account management screenshot" style="width:250px; margin-left: auto; margin-right: auto; display: block;" />

&nbsp;
<center> users can change email and/or password in user account page </center>
&nbsp;
&nbsp;

## Programming Style

&nbsp;

1. The app uses Navigation Component and view models.  Different fragments responsible for different functions.  The data that needs to be persistent are kept in the view models. 

&nbsp;

2. There are several functions in the app, like sharing routes and getting recommended routes.  They involve getting information from google map api, Geocoding API, Places API and Direction API.  They have many steps.  I created separate app states for these functions to coordinate different fragments to do different parts to achieve the common goal.  The use of app states greatly reduced the number of live data variables I needed.

&nbsp;

3. The app implements Firebase Authentication, the one uses email and password.  Users need to create an account and login to the app before accessing any function.

&nbsp;

4. The login and create user account pages, the user name, email and password field shows errors and hints every time user press a key.  When the user got all fields correct with no error, the login and the create account button will be shown.  I used mediator live data variables to achieve that function.  It will minimize the problematic entries before sending to Firebase Auth.

&nbsp;

5. The app also has a password recovery function which sends a password reset email to the user.  Firebase Auth sends the email for us.  The user can then click the link to enter a new password and use it to login the app again.

&nbsp;

6. The app also allows users to change their password, as well as the email registered.  The app sends the change request to Firebase Auth and notice the user if it is successful.

&nbsp;

7. I created user object and route object to represent the users and the routes.  I save these objects in Firebase realtime database.  There is a users entry.  All users will be saved under this entry.  Under a user object, there is routes created property which contains all the routes the user created.  Under a route object, there are several properties.  This includes address, points that compose the route, city, state, country.  I use the city property to suggest routes to the user.

&nbsp;

8. The app implemented Google Map to show the routes to the users.  The map is enclosed in the support map fragment, as suggested by Google.  The fragment is then embedded in the map page fragment which consists of the map and the menu.

&nbsp;

9. The app allows users to type in a place name to search the map too.  I embedded the auto complete support fragment of search bar in the map page fragment.  I configure the fragment to return the place information the user choose.  The map will then navigate to that place.

&nbsp;

10. Users can place up to 10 markers in the map.  The total distance is limited to 5000km.  Then, the app will post a request to Directions API to attempt to get a route from the markers.  The app then draws a polyline to connect all the points returned in the map.

&nbsp;

11. Users can then click share route button to share the route to the other users.  The points of the route will be saved in Firebase realtime database, as hashmap entries.  This is because Firebase database doesn’t support list structure.  Each point is represented as an entry in the map, with latitude and longitude as the keys.

&nbsp;

12. In the suggest route fragment, users can get recommended route by choosing a point in the map.  The app will then post request to Geocoding API to get the city the point located.  Then, it will post another request to Firebase realtime database to search the routes which located in the city and display to the user.

&nbsp;

13. The user can then choose a route.  The app retrieve the route data from Firebase database and parses it back to an route object.  The app then draw the route as a polyline in the map.  Only one route is displayed at any time.

&nbsp;

14. In the future, I might add the like feature for the routes.  That users can like a route.  It will keep track of the number of the likes.  The routes can then be ranked by the number of likes and recommended accordingly to the user.

&nbsp;

15. Please give me comments.  Thank you very much!

&nbsp;

## All right reserved
