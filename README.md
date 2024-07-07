# GameFinder
A Java library that finds 100% off games from Steam, EpicGames, and GOG.

## Dependency
[![Maven Central](https://img.shields.io/maven-central/v/io.github.theforbiddenai/GameFinder)](https://central.sonatype.com/artifact/io.github.theforbiddenai/GameFinder/overview)

### Maven
```xml
<dependency>
    <groupId>io.github.theforbiddenai</groupId>
    <artifactId>GameFinder</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Gradle
```groovy
implementation 'io.github.theforbiddenai:GameFinder:1.0.0'
```

### Non-Maven
If you are unable to resolve dependencies from Maven repositories, the GameFinder's jar can be downloaded from
the [Central Maven Repository](https://repo1.maven.org/maven2/io/github/theforbiddenai/GameFinder/).

## Usage
First, create an instance of the `GameFinder` class:
```java
GameFinder gameFinder = new GameFinder();
```

By default, there are no enabled platforms. To change that do the following:
```java
GameFinderConfiguration config = GameFinderConfiguration.getInstance();
config.getEnabledPlatforms().add(Platform.EPIC_GAMES);
config.getEnabledPlatforms().add(Platform.STEAM);
config.getEnabledPlatforms().add(Platform.GOG);
```
The above lines will enable all platforms. To retrieve games, there are two options:

The first way (and the recommended way) is to call the `retrieveGamesAsync` method.
As the name implies, this will retrieve games asynchronously.
```java
gameFinder.retrieveGamesAsync(gameCollection -> System.out.println(gameCollection), throwable -> System.err.println(throwable));
```
This method takes in two callbacks: A GameRetrievalCallback and a GameRetrievalErrorCallback.
The GameRetrievalErrorCallback is called each time an error is thrown

<ins>NOTE</ins>: The GameRetrievalCallback will be called one time for each platform. 
  * This means that each collection will contain games from only ONE platform.

The second way to retrieve games is to call the `retrieveGames` method.
This will retrieve games synchronously and return a list containing ALL found games.
```java
List<Game> games = gameFinder.retrieveGames();
```
Because some information MUST be web-scraped depending on the platform, it is recommended that you use the asynchronous method.

NOTE: The web-scraping portion is still done asynchronously. However, this method waits until all results are ready before it returns any Game objects.

## Game Object
GameFinder returns Game objects. Here is what each Game object contains
1. `title` of the game
2. `description` of the game (translated to the language provided in the locale if provided by the game's platform, otherwise English)
3. `url` to the game's listing
4. A boolean (`isDLC`) specifying whether the game object is a DLC or not
5. The `originalPrice` of the game (In the currency obtained from the locale if provided by the game's platform, otherwise USD)
6. The `platform` of the game in the form of a Platform enum
7. A map of store media (`storeMedia`)
   * This contains store art such as the capsule images and page backgrounds for Steam
8. A list of screenshots (`media`) from the game
9. The `expirationTime` in epoch seconds (If an expiration time cannot be found, this property will be set to -1)

## Configuration
GameFinder has a singleton configuration class named `GameFinderConfiguration`.
To access its instance, use the `getInstance` method:
```java
GameFinderConfiguration config = GameFinderConfiguration.getInstance();
```
As stated above, by default, there are no enabled platforms. To change the enabled platforms, you can either call the `setEnabledPlatforms` method and pass in a list of Platform enums, or you can call the `getEnabledPlatforms` and use the built-in Java list functions
```java
config.setEnabledPlatforms(List.of(Platform.STEAM, Platform.EPIC_GAMES, Platform.GOG));

config.getEnabledPlatforms().add(Platform.EPIC_GAMES);
config.getEnabledPlatforms().add(Platform.STEAM);
config.getEnabledPlatforms().add(Platform.GOG);
```

By default, GameFinder will return both Games and DLCs. To disable DLCs use the `includeDLCs` method
```java
config.includeDLCs(false);
```

By default, GameFinder will include screenshots marked as mature content on Steam. To disable this, do the following:
```java
config.allowSteamMatureContentScreenshots(false);
```
<ins>NOTE</ins>: As stated above, this is ONLY for Steam and ONLY applies to the screenshots that are accessible in the Game object.

By default, GameFinder's locale is set to en-US. To change this use the `setLocale` method
```java
config.setLocale(Locale.CANADA);
```
<ins>Restrictions</ins>: A locale must have both valid combination of a two-letter language code and a two-letter country code. Otherwise, a `LocaleException` will be thrown.
<br><ins>NOTE</ins>: If a Game's description is not translated to the language specified in your locale, it will default to English.

By default, all CompletableFutures are executed on threads found in `ForkJoinPool.commonPool()`. If you would like to change this you use the `setExecutorService` method:
```java
config.setExecutorService(Executors.newFixedThreadPool(5));
```

By default, GOG will only return USD.  To change this, do the following: <ins>**This is not recommended!**</ins>
```java
config.useGOGLocaleCookie(true);
```
If GOG does not support the currency used by the country defined in the locale, it will not return a 0.00. 
To combat confusion, the `originalPrice` property in the Game object will be set to `N/A (Unsupported Locale)`. 
Additionally, GOG will sometimes the incorrect currency. I believe this is due to how GOG caches game listings, but I am not sure.

## What Exactly is Web-Scraped?
Wherever possible, I try to use publicly accessible APIs provided by each platform. These APIs are usually undocumented and don't always have all the required information.

**EpicGames**: Nothing

**Steam**: 
  1. The discount expiration time

**GOG**:
  1. The description
  2. The original price
  3. The discount expiration end time
  4. Miscellaneous `storeMedia` and `media` entries

So much information is web-scraped from GOG because GOG embeds a JSON object inside the HTML of each game listing containing all the necessary data.
This data is otherwise spread across several API endpoints, except for the expiration end time (which is only available from the game listing). 
Furthermore, the price endpoint is historically unreliable and returns blatantly incorrect information on occasion.