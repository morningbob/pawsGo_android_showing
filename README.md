# Paws Go

&nbsp;

## Paws Go Android Mobile App

&nbsp;
&nbsp;

### Paws Go is an app for pet owners to report their lost pets.  It is a platform for users who found a pet and who lost a pet, to communicate.  The users can also message the dog owners in the app.  

&nbsp;

### The app uses Firebase Authentication to validate users.  Users need to create an account using an email and a password.  All the functions of the app requires users to login the app.  

### The app also use Google Maps to let users to mark the lost place for the pet.  It also uses the map to display the lost location to the other users.  However, the pet owners can also write in words for the lost location.

### Moreover, the app stores the lost pet's photo in Google Cloud Storage.  It will be presented to the other users when they read the lost pets list.

&nbsp;

&nbsp;

<img src=".\images\pawsGo_001.png" alt="application login screenshot" style="width:250px; margin-left: auto; margin-right: auto; display: block;" />

&nbsp;
<center> Users need to create an account and login the app in order to use any of the app functions.  This helps the app to facilitate the messaging function and save the lost reports under the user's profile.</center>
&nbsp;

&nbsp;

<img src=".\images\pawsGo_002.png" alt="home screenshot" style="width:250px; margin-left: auto; margin-right: auto; display: block;" />

&nbsp;
&nbsp;
<center>Users can change the password in home page.  There is a menu here.  Users do most navigations from here.  </center>
&nbsp;

<img src=".\images\pawsGo_004.png" alt="report lost pet screenshot" style="width:250px; margin-left: auto; margin-right: auto; display: block;" />

&nbsp;
&nbsp;
<center>This is the report lost pet form.  Users need to fill in the form to file a report.  The reports will be available in the lost pets list in the main menu at home page.  The users can use the map to mark the lost location in the report.</center>
&nbsp;

&nbsp;

<img src=".\images\pawsGo_005.png" alt="lost pet reports list screenshot" style="width:250px; margin-left: auto; margin-right: auto; display: block;" />

&nbsp;
&nbsp;
<center>This is the list of the lost pets.  All the reports filed are saved in Firestore.  The app sends a request to Firestore when started, to get all the lost pets and found pets reports.  They are shown here in the lost pets and found pets list.</center>
&nbsp;

<img src=".\images\pawsGo_006.png" alt="send message screenshot" style="width:250px; margin-left: auto; margin-right: auto; display: block;" />

&nbsp;
&nbsp;
<center>Users can send message to the other users.  When they think they found the lost dog in the report, they can message the pet owner.  The pet owner can reply too.  Or, the users can message the user who found a dog in a found dog report.</center>
&nbsp;

<img src=".\images\pawsGo_007.png" alt="messages received list screenshot" style="width:250px; margin-left: auto; margin-right: auto; display: block;" />

&nbsp;
&nbsp;
<center>This is where the users can read the messages they received from the other users.  The users can also read the messages sent in the messages sent list.</center>
&nbsp;

## Programming Style

&nbsp;

1. The app uses Navigation Component and view models.  Different fragments responsible for different functions.  The data that needs to be persistent are kept in the view models. 

&nbsp;

2. The app uses Firebase Authentication to validate users.  Besides keeping a record in Firebase Auth.  The app create an user object and send to Firestore database.  Firestore keeps the user objects in a collection.  It keeps the most updated user status in the database.  Whenever the user logins, the app retrieves the user object from the Firestore and then update the user object in local database. 

&nbsp;

3. Besides saving the user's data in Firestore.  The app also save the data locally, in the device.  The app uses Room Database.  I created User class, Message class and Pet class.  The two databases, Firestore and Room Database works together, as follows: The app retrieves data from Firestore.  It saves the data in the local database.  All the fragments in the app were set to retrieve live data from the room database.  So, whenever there is an update in the data, the fragments will receive notification and display the updated data in user interfaces.  This setting helps the app to display the latest available information if the users don't have good internet access, like a bad WIFI connection.  Because the app is not displaying the data from Firestore directly.  It is displaying the saved local data.  

&nbsp;

4. Whenever the user performs an action in the app.  The app retrieves the user object from Firestore again, and updates it.  For example, if the user reports a lost dog, the app retrieves the most current user object from Firestore, and add the lost dog in the lost dog list in the user object.  The app then sends the updated user object to Firestore.  At the same time, the app also save the lost dog in the local database.  In cases like this, the app's interfaces are updated when the app save the lost dog locally, instead of waiting for the updated user object from Firestore.  Then, the app doesn't need to send so many requests to get the user object from Firestore, in order to keep the user's interface updated.  

&nbsp;

5. Users can message the other users in the app.  But only when the user filed a report, can the other users message him.  The user can message back.  There is no add friends function in the app.  When a user sends a message, the app creates a message object and writes it to the messages collection in Firestore.  I wrote a cloud function to facilitate the messaging in the app.  The cloud function finds the sender's user object in Firestore and writes the message in the messages sent list.  On the contractory, it finds the receiver's user object in Firestore and writes the message in the messages received list.  The messages are delivered like that.

&nbsp;

6. When the user files a lost or found pet report, the app writes to the lost or found pets collection in Firestore.  The pet object will also be saved in the user object in Firestore, and local database.  Whenever the app starts, it retrieves the lost pets and found pets list from lost and found collections in Firestore.  That way, the app gets the updated reports from Firestore and displays to the users.  I will also write a cloud function to match lost and found reports, to see if there is a match.  I'll compare the pet's name, breed, lost location etc.  If I find a match, I'll write a message to the owner's user object, next time he starts the app, he will be notified.

&nbsp;

7. When the user files a report, he can upload a pet's image too.  The app upload the image to Google Cloud Storage, instead of Firestore.  The app creates a pet object, it stores the location url string of the image in the object, and sends it to Firestore.  When the app starts, it retrieves the lost and found pets from Firestore.  It also read the pet objects' location url string and downloads the pets' images from Google Cloud Storage.  I use Glide to display the images in user's interfaces.  I use a binding adapter to bind the images to the layouts.  Glide is responsible to send the request to Cloud Storage, download the image, then the binding adapter binds the image to the layouts.  Now, the users can only upload one image per report.  I will refactor the app to let the users to upload, two or three images later.

&nbsp;

8. The app uses Google Maps to show the lost location of the pet.  The users can also mark and edit the lost location using Google Maps.  When the user mark a place, I use the Google Place API to get the coordiante of the mark.  I also use Google Geocoding API to get the address of the lost location user marked in the map.  Both the address string and the coordinate of the lost location is saved in the dog report.

&nbsp;

9. The users can also edit the pet report in the app.  They can also upload another picture of their pet.  But the new one will replace the old one.  Now, only 1 image is allowed.  Users can also reset their accounts' passwords in the login page.  I use Firebase's Authentication to send a password reset email to the user.  He can reset the password by clicking on the link in the email.  Besides that, users can change their passwords for their accounts.  However, I don't let users change their emails.

&nbsp;

10.  I wrote the iOS version of the Paws Go app too.  Please take a look at it and give me comments.  Thank you very much!  <a href="https://github.com/morningbob/pawsGo_iOS_for_show">https://github.com/morningbob/pawsGo_iOS_for_show</a>




