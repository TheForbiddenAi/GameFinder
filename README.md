# GameFinder
A Java library that finds 100% off games from Steam, EpicGames, and GOG.

## Usage
First, create an instance of the GameFinder class.
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
The above lines will enable all platforms. To retrieve games there are two options:

The first way (and the recommended way) is to call the retrieveGamesAsync method.
As the name implies, this will retrieve games asynchronously.
```java
gameFinder.retrieveGamesAsync((gameList) -> System.out.println(gameList));
```
This method takes in a callback that returns a list of Game objects.

NOTE: The callback will be called one time for each platform. 
  * This means that each list will contain games from only ONE platform.

The second way to retrieve games is to call the retrieveGames method.
This will retrieve games synchronously and return a list containing ALL found games.
```java
List<Game> games = gameFinder.retrieveGames();
```
Because some information MUST be web-scraped depending on the platform, it is recommended that you use the asynchronous method.

NOTE: The web-scraping portion is still done asynchronously. However, this method waits until all results are ready before it returns any Game objects.

## Game Object
GameFinder returns Game objects. Here is what each Game object contains
1. Title of the game
2. Description of the game (translated to the language provided in the locale if provided by the game's platform, otherwise English)
3. Url to the game's listing
4. A boolean (`isDLC`) specifying whether the game object is a DLC or not
5. The original price of the game (In the currency obtained from the locale if provided by the game's platform, otherwise USD)
6. A map of store media (`storeMedia`)
   * This contains store art such as the capsule images and page backgrounds for Steam
7. A list of screenshots (`media`) from the game
8. The expiration time in epoch seconds

## Configuration
GameFinder has a singleton configuration class named GameFinderConfiguration.
To access it's instance, use the getInstance method:
```java
GameFinderConfiguration config = GameFinderConfiguration.getInstance();
```
As stated above, by default, there are no enabled platforms. To change the enabled platforms, you can either call the setEnabledPlatforms method and pass in a list of Platform enums, or you can call the getEnabledPlatforms and use the built-in Java list functions
```java
config.setEnabledPlatforms(List.of(Platform.STEAM, Platform.EPIC_GAMES, Platform.GOG));

config.getEnabledPlatforms().add(Platform.EPIC_GAMES);
config.getEnabledPlatforms().add(Platform.STEAM);
config.getEnabledPlatforms().add(Platform.GOG);
```

By default, GameFinder will return both Games and DLCs. To disable DLCs use the includeDLCs method
```java
config.includeDLCs(false);
```

By default, GameFinder will include screenshots marked as mature content on Steam. To disable this, do the following:
```java
config.allowSteamMatureContentScreenshots(false);
```
NOTE: As stated above, this is ONLY for Steam and ONLY applies to the screenshots that are accessible in the Game object.

By default, GameFinder's locale is set to en-US. To change this use the setLocale method
```java
config.setLocale(Locale.CANADA);
```
Restrictions: A locale must have both a language code AND a country code. Otherwise, a LocaleException will be thrown.
NOTES:
  * If a Game's description is not translated to the language specified in your locale, it will default to English.
  * As of now, the GOGScraper ONLY returns currency in USD. GOG as a platform also supports CAD, but that is currently not implemented
     * GOG also has a price endpoint that supports other currencies. However, this is historically unreliable, returning blatantly incorrect information.

By default, all CompletableFutures are executed on threads found in `ForkJoinPool.commonPool()`. If you would like to change this you use the setExecutorService method:
```java
config.setExecutorService(Executors.newFixedThreadPool(5));
```

## What Exactly is Web-Scraped?
Wherever possible, I try to use publically accessible APIs provided by each service. These APIs are usually undocumented and don't always have all of the information required.

**EpicGames**: Nothing

**Steam**: 
  1. The discount expiration time

**GOG**:
  1. The description
  2. The original price
  3. The discount expiration end time
  4. Miscellaneous `storeMedia` and `media` entries

The reason so much stuff is web-scraped for GOG is because GOG embeds a JSON object inside the HTML of each game listing containing all of the necessary data.
This data is otherwise spread across several API endpoints, except for the expiration end time. Furthermore, the price endpoint is historically unreliable and returns blatantly incorrect information.



