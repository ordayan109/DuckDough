# DuckDough

אפליקציית Android בעברית לחישוב כמויות בצק לפיצה, עם ברווז שף לבן ומקור צהוב.

- עיצוב הפיצרייה בצבעי עגבנייה, בזיליקום ובצק.
- שדות מספריים במקום סליידרים.
- שינוי מספר הפיצות, משקל כדור או כמות של מצרך מעדכן את החישוב.
- חישוב לפי קוטר ועובי, ומתכונים רגיל וללא גלוטן המבוססים על Dough Guy.
- במצב קוטר ועובי נשמרות כמויות המצרכים של האתר; המשקל הכולל ומשקל הכדור מחושבים מסכומן.
- שמירת הכמויות האחרונות במכשיר, העתקת מתכון ושיתוף דרך Android.
- כל החישובים והאיורים כלולים באפליקציה. אין הרשאת אינטרנט, חשבון משתמש או שרת.
- Android 8.0 ומעלה.

פותח באהבה על ידי נורמן.

See [CALCULATIONS.md](CALCULATIONS.md) for the formulas, source and the corrected
weight display. The numerical model is verified against 660 reference cases.

## Build

Open this directory in Android Studio with JDK 17, or run:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

On Windows, use `gradlew.bat`. The installable development APK is written to
`app/build/outputs/apk/debug/app-debug.apk`.

GitHub Actions builds the APK, runs the calculation tests and Android lint,
and exercises the native UI on an Android emulator. Download the APK from the
`DuckDough-Android-APK` artifact of the relevant workflow run.

The development APK uses Android's debug signing key. For a distribution build,
create and retain a private release signing key outside the repository.

