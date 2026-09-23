# 📖 変更履歴 (CHANGELOG)

This is the complete change log for the Hello Reader project.
This is a long text, so please be careful if you are using a screen reader.

日本語:
Hello Reader プロジェクトの全変更履歴です。
長文ですので、スクリーンリーダーをご利用の方は注意してください。

## [Unreleased] - 未リリース



### ♻️ リファクタリング (Refactoring)

- *(ci)* Split sync steps directly in workflow to keep repository clean ([0d4381d](0d4381d2564f06fc7edd7757d2c2d5c315318bf5))

- *(ci)* Split sync steps directly in workflow to keep repository clean ([8b7f59e](8b7f59ed9c16ff0a29e463a6a3aa386d0b93ae49))

- Optimize Morse code conditions spec with guard clauses and update HelloReaderService implementation ([bb61357](bb613570d60ae95d5ec2558f57bfc82998cc73e1))

- Strictly adhere to Nadeshiko 3 timeline specification (Japanese_Phonetic_Braille_Feedback.nako3) ([045e11f](045e11fa42e9f1fbcfefb45026befc7201ef996c))


### ⚙️ その他・メンテナンス (Maintenance)

- Expand .gitignore rules for build artifacts, keys, and logs ([3af41fc](3af41fc5c387706eaeedbae22a6568f1a6b0659f))

- *(gitignore)* Ignore local gitea-runner service scripts and configuration ([f3253fc](f3253fcab5a69e65503440e02702bd0cf4853f1d))


### ⚡️ パフォーマンス改善 (Performance)

- Perf: Optimize Android_Logcat.ps1 for buffered drive-safe saving and total lines display
- Use 64KB memory buffered StreamWriter for SSD-friendly low-load file saving
- Restore total lines count in saved log completion message
- Retain 10% progress bar indicator during file save ([7caac86](7caac86e3cf2f22b6213f016b2bcaa11a9a24619))


### 🎨 コードスタイル (Style)

- Refine self-TTS initial guidance message based on stomato463-crypto draft ([1d5d7e6](1d5d7e69142c5840366b522ec9802da74f626a27))

- Fix markdownlint warnings in AGENTS.md ([6b08480](6b0848000c8cc371d23611ea9bba47dd94ed2e9d))

- Remove unrequested hands-free slogan from MainActivity ([ba3f9f9](ba3f9f964d7dce32803cabf2970702de541af3d5))

- Format and refine .gitignore with security and backup rules ([6157d9d](6157d9d69eac9d1e1af4df96b4f9260a6fd9c9f3))


### 🐛 バグ修正 (Bug Fixes)

- Resolve logcat file truncation issue in Android_Logcat.ps1 ([7a47734](7a477341fd8295c32bb03076d3fefe835a23972c))

- Fix: Ensure real-time log streaming in Android_Logcat.ps1
- Clear filter patterns in FullMode to stream all device logs without blocking
- Expand log filter level to Verbose for DefaultMode ([f4b6218](f4b62180d76746ea71a7ce1018a3a7359db3784f))

- Fix: Update Gitea Actions build workflow for Windows host compatibility
- Replace Linux chmod and ./gradlew commands with .\gradlew.bat for Windows runner
- Specify powershell shell for step execution ([697a0b3](697a0b3bbb798438b896cb988ce963c1c38726b8))

- Restore tools/launch.ps1 and update build.yaml release workflow ([018cb6c](018cb6cfa373a00d38ce03baf10c9dd38a613dfa))

- Resolve PowerShell ParserError in Android_Logcat.ps1 progress condition ([92994a3](92994a32caf235de1b71fb0b767e367cfdcda2d8))

- *(tools)* Ignore talkback artifacts in gitignore and update logcat save path 🛠️ ([4e5eafd](4e5eafd21f4aba7701960ad21b6f892e0531c0c9))

- Kill background adb process immediately on Ctrl+C to prevent logcat script freeze ([9ef18b6](9ef18b651eba99338393e22c246d833009bebeea))

- Fix: block in-call timer repetition and add automatic privacy log sanitization" -m "- Prevent in-call timer (Chronometer) automatic repetition bug during active calls
- Add sanitizeForLog to HelloReaderService to mask phone numbers in log output
- Add Sanitize-LogLine to tools/Android_Logcat.ps1 and TalkBack_Logcat.ps1 for automatic log privacy masking
- Update KNOWN_ISSUES.md roadmap for Gboard, LINE, and WhatsApp optimizations ([e73cbe6](e73cbe63aadefb47263112631c3a6f7639d5284d))

- Remove extraneous button label from general clickable hints ([a4d7fb4](a4d7fb43bf74f546082ae37eb06931f6f2629e83))

- Block background SystemUI content changes from interrupting speech ([28cd82e](28cd82e1e9197e1e1e7406896a0b57155fd58216))

- Prioritize collectAllChildTexts over parent container description for Pixel Gemini Live widgets ([e3ed7a2](e3ed7a24ad6633cb24d6c52eadba49c3f7c9d0e5))

- Bypass 2000ms duplicate text cooldown on explicit user focus events to ensure 100% immediate speech for Gemini Live ([38372f4](38372f4053885226efdfbde13e700de9a5692f48))

- Align speakHomeScreenItems with TalkBack standard to prevent focus jumps on Pixel Launcher ([4ce7282](4ce7282c190da5f63bf1bd5e13e74978ef60351d))

- Restrict collectAllChildTexts to small widgets (childCount 1..8) to eliminate home screen text wall bug ([383d09a](383d09a5f33d5a85c3a9462743d5f4dc9a6ba0fc))

- Restore stomato463-crypto-san's warm guidance messages upon HelloReader service initialization ([e690756](e69075645612ca0fc552e65245a1300ff1361ad1))

- Remove duplicate onGesture handlers resolving Conflicting overloads error ([a1ba144](a1ba144afb58d7941bc790a64e78c3c7f6695ecd))

- Prioritize composite child text collection to read Gemini Live widget buttons fully ([ebc3eeb](ebc3eeb9fdde512a1b4e72d0f8a4c529632fcdf2))

- Skip parent widget focus when child nodes are focusable to match TalkBack behavior ([f9e3d09](f9e3d09ca2f3eea4b897e188ec8d10ee0da1d35e))

- Unify TTS instance and ensure any user touch or key press instantly interrupts speech ([1a74c90](1a74c9047662948a227e32786ffe93dc0a48033a))

- Block automatic full screen text reading on window content changes to ensure quiet user-driven interaction ([7fa09a2](7fa09a21ba648cca1146b7ec44918a91b97da5f9))

- Strictly limit Morse vibration feedback to EditBox (EditText) input fields only ([bc8de57](bc8de572be29b68a668b1243f07af2cff5f33d53))

- Remove speakHomeScreenItems on unlock and strictly bind Morse to active EditText focus ([497bd59](497bd592e78516847ba7f14c874adf70ab54c71b))

- *(accessibility)* Fix relative index calculation in navigateByHeading and navigateByControl for smooth backward/forward navigation ([32c5ee6](32c5ee631b5322cfa618440fab7dd6d609e4cdc7))

- *(ci)* Update build.yaml workflow for Gitea Actions on github-main ([793821f](793821f0ce0509bf0c8ed7d8b02dbafaf56c0133))

- *(ci)* Escape PowerShell string interpolation and downgrade artifact actions to v3 ([ced34e0](ced34e0fd074eda77e70e7dba9b18663c53c02a7))

- Add --no-daemon to gradlew build step to prevent runner timeout ([e5b6581](e5b6581ada2b8e66cd8c11162145557f5f4f38fc))

- Change build step shell to cmd to avoid runner I/O timeout ([80ef09d](80ef09d832a72a9b46731fe5ad72a02d0d3b9e01))

- Make phonetic dictionary selection always visible matching TalkBack preference layout ([cc27336](cc27336ed9b8689ccd20584dcdea2671a1821f15))

- Resolve phonetic phonetic dictionary per character type matching TalkBack spec ([54d49cc](54d49cc9e55f3a108d1a88fd2b62ef264c855865))

- Apply phonetic dictionary lookup on text changed events for IME input ([fdea627](fdea62745c22f3fe34f4a194ce3ab1bb8cd95d02))

- Resolve keyboard container focus speech and prioritize Japanese phonetic dictionaries ([797b7fe](797b7fe91b7d1c9241d6b70d3682ae8019e54f36))

- Strictly enforce 0.5s delay rule for phonetic sub-voice speech and clear stale timers on focus change ([a73b795](a73b7952fc902b4b226f7193d89819a3f35879a4))

- Parse composite key text like 'あ ALPHA' into main character and phonetic sub-voice to satisfy 0.5s delay timeline ([7541cb2](7541cb2359d558c486cf05425a06efb4e23966d0))

- Map Romaji keys to Japanese Kana for mainText speech and ensure correct Japanese phonetic lookup ([d32eaff](d32eaffd1347c352f4fd5c18aeeb2a875c7e389d))

- Resolve Gboard composite 'a ALPHA' key text to Japanese phonetic 'あさひのア' ([3a86af9](3a86af97893128fb282fd753b27f23de212ca9a6))

- Resolve IME language detection for Japanese vs English keyboard speech and ensure clean service restart on deploy ([d839a9b](d839a9b53ea146efd1aaf4a1d9abdb84ab0e59e5))

- Prioritize manual IME language mode detection over sticky ja_JP InputMethodSubtype in Gboard ([c424195](c4241955fe297e1dbc761185528cdf197cf03a2b))

- Process TYPE_ANNOUNCEMENT in HelloReaderService to handle Ayame IME mode changes (英数モード/ひらがなモード) ([9b14328](9b143282815ecba4738d54d382730891516355fe))

- Support Gboard native key descriptions (a ALPHA) in isEnglishLanguageActive for seamless phonetic reading ([5be8278](5be827872fde087816b9455ba54e0c7526945194))

- Remove duplicate performBrailleHapticFeedback method declaration ([c07a084](c07a084bbe6b21afed7f722dbd81572b2b8cf6c7))

- Resolve speech and braille feedback during Gboard slide navigation ([863023f](863023f644759e19ec1d4e2beaf2363de1de8e3c))

- Resolve missing speech and braille haptic feedback during Gboard touch exploration ([8039d90](8039d90a746621d5820e7e710f19b9b6f0df8187))

- Strictly restore 0.5s delayed braille timeline after speech completion ([2e242b3](2e242b384249eff07efbd84c602d99f558f2373d))

- *(gboard)* Resolve touch exploration speech and 0.5s braille feedback timeline ([672d211](672d211a0e0e039c0a46cd1723d226b64288c1b0))

- *(phonetic)* Resolve Japanese digit Wireless Regulations readings and preserve universal 0.5s braille timeline ([05c8ccc](05c8ccc79462ccfd2b5ec636c35ad90d824ff0c2))


### 💡 その他の変更 (Other Changes)

- Initial commit ([3721371](37213710c3a85ab999c4fdf008c6d95485a47a34))

- Added a screen reader development plan. ([be417db](be417db462d2c826cf948d30909c0f98c0855936))

- Add to agent.md ([a9b8b96](a9b8b9607f0a1badddf512d08f5ba581ffe71cef))

- Add Gitea Actions build workflow for Gitea Runner ([089aa4c](089aa4c5c9b9d815fe238a1ce19c5132b23635f3))

- Add the runner extension for gitea to gitignore. ([4190b02](4190b02cbbdbdb421ac8f4a44a25f83f60e00698))

- Update workflow runs-on to host for Dockerless execution ([3525d3a](3525d3a1198a0d8d0fc07db859252280c26d8b59))

- Update workflow runs-on to host for Dockerless execution ([3da784f](3da784f210e1ab9fd89a3020d675818a81834147))

- "feat: Add Morse code deep sound feedback for Ctrl+C and save completion in Android_Logcat.ps1
- Play Morse 'A' (. -) beep on Ctrl+C keypress for instant acoustic feedback
- Play Morse 'E' (.) beep on log save completion ([18d4434](18d443498b25321ab670cb90d87af84ea74e6e93))

- Add Talkback to .gitignore ([8536ce5](8536ce54d07657bdb3a8ba1fb92c0ae7dd08b259))

- Added material3 support. ([dee4a48](dee4a487557bfbdc628b2de902b38ad3dcbad8ab))

- Add various utilities in tools directory ([14524ff](14524ff824be8a0b6f0e478c3acb593f76d2b598))

- Fix Kotlin compiler warnings ([58a08b2](58a08b20b43c485a1f48c9dafdde04ff5c558aeb))

- - Added information about Null pointer errors in input areas to the list of known issues.
- Add haptic feedback ([b885103](b8851037148d9c54f2cff1ed4961a997d1531568))

- Refine Logcat mode filters to focus on HelloReader tags ([eccedff](eccedffabb2b08f8fe093008c0ed196278121641))

- Align workflow APK naming and desktop output with AGENTS.md policy ([401e8e5](401e8e53527495738e9a5027387f913280f43823))

- Set checkout fetch-depth to 0 and use env vars for runner path ([9407f39](9407f3920b9fa178db304e19f0627fe87acc50c0))

- Adjusting .agent.md ([f1a8b88](f1a8b88cd3a69f44f7dc226bf3720a5657114cb0))

- Implement Sub-Voice Hint Guidance for dual-tone TTS accessibility reading ([7287087](7287087b846a37e1ed2e2349aa86ff1432621869))

- Fix TTS muting bug using UtteranceProgressListener for sequential sub-voice playback ([fe00668](fe00668b640f7212c2bb0170d9b6d614686f9794))

- Fix TTS muting by replacing dynamic setVoice with reliable pitch modulation ([f3a7ef6](f3a7ef67721976a7c2cd813245325235d0588a61))

- Enhance node parsing with parseNodeDescriptionPair for dual-tone Sub-Voice hint reading ([b00c208](b00c208ebbdf6a7da43aee0c4def5a8c84a44be1))

- Restore speak method and fix compilation error ([a5f740b](a5f740bd0e6a7a7a36a7778a9ce2ae89bee3c014))

- Remove extra closing brace in HelloReaderService to fix class scope and compilation ([d899e0d](d899e0db1fd6891773eac4a7898a9a98178d3c7b))

- Remove duplicate speak method to fix overload resolution ambiguity ([e935f86](e935f86284b5c7097f9067b0445b80258e43be25))

- Merge branch 'dev-tts-sub-voice-official-pheasant-harlequin' into dev-straightforward-crab-aqua ([3ae491d](3ae491dda27b96c6641a8e6dd233150978fdfe3a))

- .agent/AGENTS.md -> .agents/AGENTS.md ([bef13c7](bef13c71ab54145a72175917d835e66eaac839c3))

- Enhance accessibility permissions, VibratorManager API compatibility, and home screen speech handling

- Add background execution, WAKE_LOCK, and package query permissions in AndroidManifest.xml
- Fix deprecated VIBRATOR_SERVICE warning by introducing VibratorManager API support for API 31+
- Fix compilation error related to nullable Vibrator instance in playMorseVibration
- Implement dynamic isHomeScreenPackage detection to ensure reliable home screen speech output ([3ae72af](3ae72aff87da38ce27488654ea21a127cd343d19))

- .agent/AGENTS.md -> .agents/AGENTS.md ([30ef3d1](30ef3d1a294d7fa65986b8d977ba8c0adb1fd974))

- Merge branch 'dev-straightforward-crab-aqua' ([1d7102d](1d7102daf363ce64b8e9685c4d75459be084d9f3))

- Fix duplicate viewId declaration and type conversion in parseNodeDescriptionPair ([6edb789](6edb7890c8b317afca113dadcc9d1461a7428a80))

- Clean up unused context parameters to eliminate build warnings ([e32f772](e32f772dccc17b62d441c47b46c2188393e6868b))

- Enhance AospSettingsProfile to recursively extract titles and summaries from settings list items ([5d883f2](5d883f2330496dfdc826fd5c1531bcee1f8f4b28))

- Integrate full Jetpack Compose app structure with Scaffold and NavigationBar ([6756fc9](6756fc9bd99019d199308c7adf528f929f1a978a))

- Add try-catch protections and clear pending Handler callbacks to prevent freezes on PIN entry screen ([cb055b0](cb055b0d0a0f5ba54f4b9d80aae014a8f95f21a9))

- Support 2-finger swipe up to launch PIN entry screen with physical drag gesture ([c6679cf](c6679cf173d1af681343491b65f4862b2245fd74))

- Enhance PIN entry screen display with KeyguardManager API and swipe fallback hybrid mechanism ([310e864](310e8644a672b6166f0c2d55ece52a3a4e3245f6))

- Fix lambda return type mismatch in showPinEntryScreen ([9bbb7f1](9bbb7f19fafe3749c85b01e90c4047d438e3605b))

- Cover 1,2,3,4-finger swipe up gestures and add LogCat tracing for keyguard unlock ([93feca4](93feca44690e0c3169b3f3f24f2852f0e1789526))

- Implement official KeyguardManager requestDismissKeyguard via MainActivity for PIN entry screen display ([d065849](d065849437e358a336597badf9472634a9e78f91))

- Add KeyguardManager import and fix onNewIntent parameter type signature in MainActivity ([e27cb24](e27cb2474c11fdb7ac70a9dd68590a9466da4ed6))

- Add PIN keypad View ID rescue extraction to ensure reliable digit reading on keyguard ([f259cb3](f259cb3ee30833ac98cc6e43d3d77ce0fd4087a5))

- Fully cover parent and child View ID digit extraction for PIN keypad nodes ([ca0d8cb](ca0d8cb8796f028a7580e1a5d81f10dea4dabc94))

- Add event text fallback and try-catch safety net to prevent errors during keyguard node parsing ([a73818e](a73818e3f6560810d54d47893ab8f6f4729c9842))

- Ensure keyguard window inspection flags are fully preserved in serviceInfo and XML config ([cea6f22](cea6f220fba389967bb6606b8fd40dfd6ecc444e))

- Prioritize physical gesture tap when keyguard is active to bypass ACTION_CLICK security blocks ([c3bd453](c3bd4534eceb1faf889e9e31d2b2d897e28eca43))

- Add findValidBoundsInScreen for precise physical tap targeting on keyguard PIN keypad ([0ff453a](0ff453a5507e89a8038cb2149a67f7d4bbf412c4))

- Adopt AOSP TalkBack official NodeActionPerformer click pipeline for reliable keyguard input ([aed9bc3](aed9bc30f483c4bec5ca7cbc7522aabfd4e54576))

- Fire both ACTION_CLICK and dispatchGesture physical tap simultaneously on keyguard to support multi-OS channels ([a8f3117](a8f3117c085eaaf17caf17331a8458e0c0d16153))

- Fix navigateFocus node collection bug during Keyguard state to enable focus movement on PIN keypad ([865b01d](865b01d160ae16a69a366abf9308dbcd6e4d2610))

- Collect nodes from all window types during keyguard to eliminate Not Found errors on PIN keypad ([e982c62](e982c62ba08faee9abf2a7bd44f47af68889b0ea))

- Add sendDigitKeyEvent to ensure digits reach the keyguard PIN input field directly ([7ad4342](7ad4342ef47e886cb3c49c555c6a2c03747a0263))

- Fix isRedundantChildNode bug to allow PIN keypad digit buttons to be fully focused during swipe navigation ([dbb07aa](dbb07aa21bcac5a62aaad4324fd715d472fff33b))

- Allow keyguard and view-id nodes without explicit initial text to be fully collected for swipe navigation ([11df189](11df189c34f54c34a90c98c0cd9304444fa37e8d))

- Implement visual traversal sorting by screen coordinates to align swipe navigation with TalkBack ([7f1df41](7f1df4183225b6cce787eb170947b93da77d196c))

- Add child node focus fallback and explicit LogCat tracing in navigateFocus ([67d4e23](67d4e23a4ecbcbdc73a09e1ea229871e95e9a010))

- Add numeric gesture ID compatibility (1 for right, 2 for left) and detailed onGesture LogCat tracing ([d144652](d1446524497fc56273a94492b2f38de57cdc0b98))

- Prioritize lastFocusedNode during keyguard and enhance isSameNode matching for 100% focus retention ([24c8976](24c8976e93021895599e55cc87a63be9ee585e6b))

- Rescue blank text with Key or Button label to eliminate generic Next Element fallback speech ([e3494b3](e3494b39bf34a21cb7df469afeca9c40034f4792))

- Bypass isFocusable restriction during keyguard to collect all PIN keypad area nodes without exception ([2152082](2152082711b5d8e4febd5d08e350184e4be2f710))

- Trigger GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE and return true on swipe up to ensure transition to PIN entry screen ([b545464](b54546450f7d5962fcced3bb14623718a067a770))

- Trigger PIN entry screen transition on TYPE_TOUCH_INTERACTION_START with GLOBAL_ACTION_BACK fallback ([c6054d7](c6054d7ffa399a70a024b3484886c5e63f253c70))

- Remove TYPE_TOUCH_INTERACTION_START handler and add 3s cooldown to prevent runaway infinite loops ([4722749](472274972a1a09def2dc6ee3c2cfda5530a9ce49))

- Align unlock swipe duration (450ms) and distance (90% to 10%) strictly with AOSP TalkBack ([1fc5d4f](1fc5d4f76448df03464d96fab69db30df8a6e47e))

- Remove BAL restricted startActivity call and rely purely on official AccessibilityService actions ([31c373f](31c373fa2e8632a28b65b06db9fee605e0048508))

- Enforce API 35/36/37+ and QPR/Canary beta multi-channel dynamic capability flags ([361b941](361b941a6ea9d28ca44d830ec066f0dd57cb3b8e))

- Merge branch 'dev-straightforward-crab-aqua' ([e73e0c0](e73e0c0fd34af19f3e9f017515bc89d01396ce0e))

- Add tactile haptic feedback when recent apps overview gesture is ready ([8956e50](8956e5012cc4b41733e3fd1d0ccf8b26cfe233cc))

- Synchronize tactile haptic feedback with OS Recents task snapshot completion (150ms delay) ([e76656a](e76656aeaa00f7c9341dd36dc737316b824c2086))

- Safely restore KeyguardManager requestDismissKeyguard integration for 100% lock screen unlock success ([d83c6a6](d83c6a66f832812909a88b6f98ad74c02cb5c998))

- Fix 2-finger swipe up gesture to correctly trigger GLOBAL_ACTION_HOME and speak home screen items ([6a58f5b](6a58f5b4de16cba8c481906be3522b298914fd43))

- Add onKeyEvent handler to immediately stop TTS speech and clear queues when volume or power keys are pressed ([a866517](a866517e3c4c41e1b1319cefcb12ee1ec0e33c4a))

- Return true in onKeyEvent to firmly intercept volume and power key events for 100% speech cancellation ([afddffb](afddffbb1258aed82fd139089393940d413bb511))

- Add GLOBAL_BUTTON_EVENTS and INJECT_EVENTS permissions strictly matching AOSP TalkBack manifest ([d0d88ef](d0d88ef3601c87cee079db819ed2afbb29ef45dc))

- Introduce isMutedByKey lock to block asynchronous sub-voice re-triggering upon key press ([4aa4bd4](4aa4bd4b83182d6ccdd5d05f6eee0db0beb48747))

- Fix unresolved reference isMutedByKey error by declaring the property in HelloReaderService ([0b2244a](0b2244a024b6846750179506279a2cbbdf0255fb))

- Expand minSdk to 30 to support BrailleSense 6 Mini (API 31) and API 30-37 multi-channel compatibility ([fa33a86](fa33a86a1497a40b92a6758c5a9ef1f448b5d543))

- Merge branch 'dev-straightforward-crab-aqua' into dev-app-support-silky-crayfish-tan ([12cecda](12cecdab94976fb1c1b2ca25628abcddcecf0d05))

- Merge branch 'dev-straightforward-crab-aqua' ([d678550](d678550f1e4440314c05a6039e7844c3e7863dde))

- Exact HelloReaderService.kt from github-remote-main while maintaining modular architecture ([2c4ac8c](2c4ac8ca57fad030da5432b6dfd0e2d040349413))

- Merge branch 'dev-straightforward-crab-aqua' ([c2dbc73](c2dbc73805c1f61dcb61b66f90b98ebc39299d30))

- Fix Morse vibration triggering outside edit boxes by restricting to editable nodes ([8b302d0](8b302d098a3bdbf27e430c1da9217e462b84f1b1))

- Fix unresolved reference node in speakWithSubVoice ([47c79f9](47c79f98b021d1a39d5c0c9fab5386b1273db385))

- Fix focus jumping to top element (weather) by improving node matching and adding spatial distance fallback ([e8229c0](e8229c02b6a0a01d8fa3b6b8eea47236b513cb26))

- Implement native focusSearch traversal based on TalkBack TraversalStrategyUtils mechanism ([ad6f26f](ad6f26fa152570c29cfda1ba256087aa9ab1e6ac))

- Fix syntax error caused by duplicated unclosed comment block ([2c24005](2c240059059f387172bcd53081598f7792aa1b7a))

- Add 800ms text deduplication to eliminate rapid double speech on focus events ([877f728](877f728ff435bb00a3e2ff7dad2a90b29d6a7690))

- Ensure sequential traversal of inner widget buttons while preventing duplicate speech ([857b6a8](857b6a8e2f5d744a769d409c80e32dac08121361))

- Prioritize contentDescription over text per TalkBack AccessibilityNodeInfoUtils standard ([fdfde46](fdfde46b1944decee8aacafb53f7e0e4e235bdba))

- Merge branch 'dev-straightforward-crab-aqua' ([6cca870](6cca870281b10dd85c9c1fd3d9b9dadf5899baf1))

- Merge branch 'dev-straightforward-crab-aqua' ([249060a](249060a3fd2b2a99227e9790ddae6a554d83c245))

- Delete the .md file that was outside the folder. ([ebaaa1f](ebaaa1fb590d653bdf4360b089e016c7294fe48c))

- Added a to-do list in the style of the Japanese programming language "Nadeshiko v3". ([4adcab2](4adcab216f98224b4037113b67e7713a058a32bb))

- Merge branch 'dev-straightforward-crab-aqua' ([a96f094](a96f094df5dcdc319a06dd350e571842874ea5aa))

- Build a support system similar to Japanese programming language Nadeshiko v3 ([beb8a9b](beb8a9b78a859b460f753d21229c9fd7fe3099c1))

- Merge branch 'dev-straightforward-crab-aqua' ([74347a3](74347a3687d39b113299e79c0d3da86fac7ec976))

- Add clean task to gradlew build step in Gitea workflow ([9da51cb](9da51cbc2b5fe043f4d2ffcc88878e5a17fc4ae8))

- Merge branch 'dev-straightforward-crab-aqua' ([1289274](1289274aec468836347b08a49c98129d535f286a))

- Merge branch 'dev-straightforward-crab-aqua' ([8354b14](8354b14352849e1fe9f00b903a2eca25b8708347))

- Add auto cleanup workflow for releases older than 31 days ([44d1cd8](44d1cd881312b20b550f3341521c41f7ea30603a))

- Merge branch 'dev-straightforward-crab-aqua' ([78a6d6e](78a6d6e0f181461325d61fd2999f38c1c15f5dd9))

- Add auto tagging, release notes, and apk release deployment for GitHub ([59b695b](59b695bedcfb41b7d6ec6e70b24fef648596deee))

- Merge branch 'dev-straightforward-crab-aqua' ([5727a72](5727a72d9d5eb9885b044553f42e267c602f123e))

- Merge branch 'dev-straightforward-crab-aqua' ([c4e5c87](c4e5c87400872da9dd2d5eda8f4a3a7ea02937f1))

- Clean up to .gitignore ([e4381f1](e4381f1a861da8189cb9f58155b75a907e16c21c))

- Update warranty disclaimer section in README.md ([a697444](a6974444add47f87bebd7de437dfd6741e9854fb))

- Update warranty disclaimer section in README.md ([226efd9](226efd931491145adc96f0c36b68819fbba8b398))

- I've revised the personal letter to a message for all the participants. ([688172e](688172e721bf83552c6c1531fd5c7e1054c71f68))

- Merge branch 'dev-straightforward-crab-aqua' ([f7e4278](f7e427852aaf361e090f3ff0f3c94dc22c64f23c))

- Merge branch 'dev-straightforward-crab-aqua' ([55a0e68](55a0e6847ed19cace1e2cb0605d4a1fe3ca195b0))

- Merge branch 'dev-straightforward-crab-aqua' ([90b243f](90b243fcd30b8a50746a9dc8c2ff0dc820aa100c))

- Merge branch 'dev-straightforward-crab-aqua' ([27ce212](27ce212251f375c966080eb2ca18c9a2d827da83))

- Introducing Nadeshiko Skills ([53173b9](53173b947519f14dafdb8d90829efc50a0c39d00))

- Merge branch 'dev-straightforward-crab-aqua' ([29e6375](29e6375c78132c35d4fb2291a8c5fc5bf82efcbe))

- Merge branch 'master' into github-main ([55683b4](55683b4a216d375cb75f5569014389d5f545b92f))

- Add Esperanto braille support and haptic feedback methods ([edcdfc5](edcdfc57a27ce52a35cd4285fce83cf8b501fe20))

- Merge branch 'dev-straightforward-crab-aqua' into docs-complicated-tarsier-blush ([ee25aaa](ee25aaaac401ed7684cbe25c6415440e15dcc381))

- Added instructions regarding the guidelines for creating the specification document to .agents/AGENTS.md. ([60692f1](60692f128a4438a2b01b6432d2ba9db71499b356))

- Fix syntax errors and update control flow in HelloReader_Menu.nako3 for gonako ([5de13c5](5de13c5e07a68ea034e88a3e55eabd142fe1a4a4))

- Update nadesiko3 skill guidelines with gonako lint error prevention tips ([0690d61](0690d6172e8d2fcf6e41a27fb84b08dd0b35ba7e))

- Implement HelloReaderMenuManager Kotlin menu system based on HelloReader_Menu.nako3 specification ([477c608](477c60888ac4605f5994e03cc278aa564b57db63))

- Fix conflicting onGesture overload by integrating menuManager into handleGestureAction ([15bbf18](15bbf18d50417543f2adf970b0927fdd3bb1cd1f))

- Fix overlay dialog theme and window flags to prevent background touch passthrough ([46d356a](46d356a4c2321832d9c3aba315e34fb93d973799))

- Add log analysis summarization policy to AGENTS.md ([1f9211f](1f9211faeac205b5eb8398c60a7ba178f875f702))

- Set explicit accessibility focus to menu dialog decorView ([149cbb1](149cbb1a0939d9a3bb2d22f9868dae384ee0b7ba))

- Migrate menu overlay to WindowManager addView and remove legacy menu call ([5d64022](5d64022b736b6e4ddfa490adb544303b9601c041))

- Fix gesture ID mapping to support gestureId 16 for upper-right swipe gesture ([fd3654d](fd3654d86af95171124751882800a4fe07615d5c))

- Add missing AccessibilityService import in HelloReaderMenuManager.kt ([bf91344](bf9134439f26f354ee139c4e58a99ac42a1512a7))

- Add Kotlin import completeness rule to nadesiko3 skill guidelines ([eb1d294](eb1d2940d0d09f0e74720658945cbb5b56c9d6b9))

- Focus only first list item on menu open to prevent reading all items at once ([7420316](742031642de669f55ce5b8b94e498b9f7aa73bcb))

- Set container importantForAccessibility to NO to prevent reading all items at once ([0d7014a](0d7014a7d218acd96400aa2c97e560f37ec3f330))

- Move screen reader plan ([1c5f438](1c5f4381ffcb248541e3190382865cba1b9ce9d5))

- Add study materials ([16472ca](16472cab8892b7b1cda3815a50fe986a382720ba))

- Unified .AGENTS.md for public use ([b1bf5cb](b1bf5cbadf7c8cb802a9b4a979e9062890bb4cad))

- Add compressed file extensions to .gitignore ([f460374](f460374c7c0fe383348d22b0ca1accfbbed3abde))

- Fix braille haptic feedback output and waveform generation (#1) ([b81433e](b81433e99633ba73095ebbbe3d5332b050522d0f))

- Implement braille feedback activation conditions based on nako3 spec ([e3a4bce](e3a4bcebb95abb3b538e8681377c70f38e09fd06))

- Automate all IME keyboard detection for braille feedback according to nako3 spec ([72820be](72820bec50b3a6052092608db1fe3a909dafcb8e))

- Fix issue #2: Implement multi-layered IME detection covering Ayame, Gboard, Sumire, Simeji and all keyboard apps ([3b2af1c](3b2af1c160d649a8665e000fbe9c231abc6531c3))

- Added Braille feedback specifications. ([c044215](c044215699950e02ba64673eaad07f53ecbf7f27))

- Fix braille vibration storm on keyboard open by adding length guard and debounce ([f54368c](f54368ca29b35fe7203819f4aa09abef19e1454a))

- Fix ultimate root cause of braille vibration storm by unifying all braille calls and adding fallback guard ([3e55bbb](3e55bbba2287566d28ee9f288b92209c01537015))

- Add missing android.util.Log import in HapticFeedbackManager ([93da5e3](93da5e3a0be61bf77e3a053c37ca1ea176d3155f))

- Refine braille haptic feedback targeting key touch and character input strictly according to nako3 spec ([dc0967d](dc0967d42275d89a928c2e0cc73bbdbda599d5fb))

- Implement unified multilingual timeline feedback sequence with 0.5s delays ([7218759](72187590aa9f088b0e4a70128a295c613dce24b3))

- Restrict braille haptic vibration exclusively to keyboard active screen and character rotor mode ([6ef227c](6ef227c473794fdf992ad22f5e80c974567e95a3))

- Enable braille haptic vibration and subvoice timeline during explore-by-touch key exploration ([3443a1b](3443a1b368cef75448abaee9bc6326faba176c11))

- Fix braille haptic vibration strength and clear pending delayed timers on new utterance ([c4d4676](c4d4676c0ff1a7a3695974fa4382350107c132d9))

- Fix conflicting local variable declaration in HelloReaderService ([54f8abd](54f8abd1e6d0f7ea28594d94f7ea6a9021b71c16))

- Omit braille prefix symbols during single-character key haptic translation ([f2a720b](f2a720b9a0dd31dd53a477c47804da9ec03f0595))

- Integrate Body-Surface Braille (体表点字方式) as core braille haptic feedback ([07b89a1](07b89a16307b94b13528a72abe3fdfe5085d0e61))

- Fix Unresolved reference TYPE_VIEW_HOVER_MOVE compiler error ([91dd736](91dd7365d8d5d63ecced068fa262578136df3ff6))

- Isolate braille haptic timer to dedicated brailleHandler to ensure 100 percent reliability after TTS ([dc5d043](dc5d043cb04eae10d74dcb50cbf1f1866e73a7b2))

- Fix instant Body-Surface Braille haptic trigger for single characters without IME or TTS delays ([bb34c2c](bb34c2c29330ec7521f0175dbf76eb8d7ad433ef))

- Harmonize strict 0.5s timeline rule (Voice -> 0.5s -> SubVoice -> 0.5s -> Braille) with backup safety timers ([e0078a6](e0078a6bf45112440da962c34dd191fe3ec768f5))

- Enforce strict 0.5s braille timeline during Touch Exploration and eliminate 0ms hover click interference ([8b3599b](8b3599be6d3b73073fa5b03a4f1b8ffed068d446))

- Add initial japanese_Phonetic_alphabet.json for issue #4 ([e846702](e84670233b62fe232fc0ffe4ef2ddd636b65e8d4))

- Complete custom Japanese phonetic alphabet JSON for Hello-Reader mode (#4) ([2619169](26191698dbde78f7e09297c1777179d07cd1c07a))

- Add traditional Japanese phonetic alphabet JSON file for mode switching (#4) ([eaf15d2](eaf15d2f748d9284b4afa5cefe4b8ac37ff56b09))

- Correctly rename an incorrect file ([02f8c26](02f8c266a0e74502142cc608a1fdae0dbe03ccd6))

- Excluding IME keyboard source code ([c32f753](c32f75305a377c526ea092c55c708dff12e66842))

- Updated to /gradle/libs.versions.toml ([85bac8a](85bac8a38352a6534f37845dcfe63c24a542f715))

- *(gboard)* Resolve Latin key phonetic dispatch in English mode and enforce universal 0.5s braille rule (Fixes #5) ([d08af5a](d08af5a60663cfaecbb4ef2f59bc8a6a76ec2966))


### 📝 ドキュメント (Documentation)

- Update AGENTS.md with verbose command policy and fix Gitea workflow branch triggers ([15a0a24](15a0a244383b557ed3bfc504eb66ed3286cbd2db))

- Update license section in README.md to reflect Clear BSD and Apache 2.0 ([485c0b2](485c0b21b0a71a6cbeae04d5142faa9f32b11c39))

- Generalize maintainer references from personal name to developer/maintainer in project policy ([f4eb8b3](f4eb8b3fbd07a1129bb338f9c1806e9adf0a3647))

- Add Project Policy and Contribution links to README.md ([3c5bd91](3c5bd910ab911ccfa2243815b49563cafa50201f))

- Add Debian-style NO WARRANTY disclaimer to README ([acdb545](acdb545e5f44673071bf2c562194af50bc2489d7))

- Update Emergency recovery spec for keyguard suppression logic ([d450b5e](d450b5eb20219e89bd91bf8d05cbc5ea8b74aad1))

- Clarify keyguard and main process survival check in Emergency recovery spec ([36b1d57](36b1d571167fd497033c3a794e29bc8791a9a90f))

- Update README with privacy policy and Watchdog security guarantee ([c7b6a50](c7b6a5067354b7164231b639144e055d673aad51))

- Add debug keystore build note to KNOWN_ISSUES ([d86cf21](d86cf214a14fa535e221241d8d5137d116f788b3))

- Add hello-reader specification guidelines for Nadeshiko 3 ([cd39dec](cd39decca4a2c51611351f710ad5e047a9529316))

- Add nadesiko3 specification rule and flexible dialogue policy to AGENTS.md ([c3a42e2](c3a42e2ef1860ce435e45b757a3c449caf0a1838))

- Convert Emergency recovery spec to flat nako3 guard clause format ([cef8dde](cef8ddeb6fc260cdd32e6003e1c3edd7a6a463e1))

- Add developer response note to Support-System.nako3 ([03c76a0](03c76a0f9ee0a0d57bc939a77f5fa06e5e469c0e))

- Remove deprecated .natk Markdown specs in favor of src/*.nako3 specifications ([6766186](67661865407b0d3adbac269118cfd6c34dbd4964))

- Add braille tactile feedback specification in src/Tactile_braille.nako3 ([c3b549a](c3b549a27ff1bcb09a00bf1961828b1258f59e5d))

- Add Nadeshiko 3 specification for Hello Reader context menu ([0b18ebd](0b18ebdaab9570d1e55be6410ea2706917d83e2d))

- Add nako3 specification for Japanese phonetic braille feedback ([0f2061f](0f2061f2929e0e96039c03f961b0e10b7c5d1a66))

- Update nako3 spec to support traditional mode as default phonetic dictionary ([a1f5b6e](a1f5b6eda9331b5caf81385130722ed29c228457))

- Docs & fix: ensure ASCII consonants like 's' always resolve to NATO phonetic Sierra in both modes while vowels dynamically switch ([f935b6e](f935b6e60c531a69f673dc12268b43a27fac7203))

- Docs & fix: document Gboard/IME behavior in KNOWN_ISSUES.md and implement universal phonetic resolution for English vs Japanese modes ([3bc5a3b](3bc5a3b874c115a6f24a8e0f822e1dbeaa2e2390))

- Add rule to always use a 4-agent team for teamwork-preview ([0d2d666](0d2d66609c7d8aebbb168ea7493a51193a9770b0))

- Add log quota saving policy to ignore normal logs and analyze only major errors ([1500b9f](1500b9f0de6795468d16f0d08014ecffb5b13341))

- *(agents)* Refine log analysis policy and add command pre-check rule ([947f42e](947f42e276acbeef4d5c1260ce8b12a7b12f3d59))


### 🚀 新機能 (Features)

- Add automated build, deploy, and launch scripts for HelloReader ([32e22f8](32e22f86b4939f084b7c4e9dc68bb3f8592148ef))

- Add and configure Android_Logcat.ps1 tool for HelloReader debugging ([4c9e6a4](4c9e6a4e8e26e6c01044d523b3819c4fb9143e94))

- Add progress bar log saving feature to Android_Logcat.ps1 ([d09156a](d09156aca9fcc58f2e43a1f042a996be346bf6a4))

- Feat: Add automatic 1000-line log cap and slim filter to Android_Logcat.ps1
- Automatically trim saved logs to recent 1000 lines to protect server and disk space
- Refine default filter for pin-point HelloReader and accessibility logging ([d38c259](d38c259afe067cbdf1dd750fa8e0e070c236f3d4))

- Add Audacity custom audio feedback (activate/deactivate) with SoundPool and update gitignore ([d5d5275](d5d5275234dc9edd65858eaa17aff9dcd9bc8060))

- Add hybrid audio feedback, double tap reliability, and phone call handling with duration announcement ([4f3a014](4f3a0144e2435ed4bc9a8d95ca46ea6d0a068683))

- Standard AOSP implementation with custom audio feedback and navigation fixes ([7e3bded](7e3bded988e08f9a94cbd8b5f58e9ae0fb9c4fdd))

- Add phonetic & morse haptic feedback, TalkBack-aligned home screen focus, and resolve compiler warnings ([a3503c1](a3503c1d2de5fc8c0a910d93838612c013110198))

- Add TalkBack-aligned scroll tones, item position announcement, notification speech, 2-finger scroll gestures, and auto-scrolling ([4e29f51](4e29f51ce1cfa33a42787e8a23f99ae4c8223a01))

- Add -c, -l, -w options to run_all.ps1 and apply 150ms sound debounce ([8e826b7](8e826b7130af7818b653da0a3cb0c010b19124be))

- Standardize speech to TalkBack Japanese, fix scroll sound loop, add caller name lookup, and implement pre-setup self-TTS guidance ([57b0137](57b0137bf77dc3d1b8096e6147c67482dfca79cf))

- Add child node search and dispatchGesture coordinate tap fallbacks for double-tap click ([7be7d24](7be7d243ac17a953635c67cb98b63ec7f525ff61))

- Enhance floating action button and shortcut button node parsing ([0cbf650](0cbf650075c3b4b53964edc4ba13d6b8291c082b))

- Feat: enhance double-tap reliability, floating button parsing, and single-flick navigation" -m "- Add child node search and dispatchGesture coordinate tap fallbacks for double-tap click
- Make phonetic code and morse vibration configurable with default OFF
- Implement custom node deduplication (isRedundantChildNode) for smooth single-flick navigation
- Enhance Floating Action Button and Shortcut button node parsing and child text fallback
- Clean up deprecation warnings in HelloReaderService ([6b4c199](6b4c19952715177e9dcdb40d9e8332a4de05c4c5))

- Feat & fix: merge sub-voice guidance and node role classification
- Integrated Sub-voice guidance feature (main text & hint pitch modulation)
- Fixed element role detection hierarchy (Switch, CheckBox, RadioButton, Button) ([31de606](31de606a95ca5a973f3bde869df98c89e6ba6385))

- Implement collectAllChildTexts to fully parse Pixel Gemini Live composite widget texts ([d2c0c0f](d2c0c0f031259e5d59017ab9297633624734279d))

- Integrate completed user pronunciation dictionary editor in MainActivity while maintaining 3-tab clean layout ([b0cdbe1](b0cdbe11b2d7252f0dace83bae0cd6914b86ab03))

- Add Bluetooth Braille permissions to AndroidManifest.xml completing full modular integration ([da77bc5](da77bc571ac56fba3b337e53062e8175aa7561f0))

- *(accessibility)* Add heading semantics for instant screen reader navigation and obfuscated mailer SOS intent ([f673d8a](f673d8a030c88b80c968c685b69036f6641ed7bc))

- *(diagnostics)* Embed in-memory ring buffer for live runtime gesture and speech logs in diagnostic reports ([6494222](6494222f9a7d26d6b78550929cae5868555c479e))

- *(support)* Implement 4th Support tab and obfuscated diagnostic mailer per Nadeshiko v3 spec ([58a87bd](58a87bdcd60ae34e91a5eccca2207d6ef4f245dd))

- *(service)* Add emergency TalkBack recovery logic with plain-Japanese guidance ([df3a69d](df3a69d4f936d09b196ad71ba9dd6eb553a2033f))

- *(braille)* Add dedicated Braille device pairing dialog and service integration ([9fbf964](9fbf964f7ee4eaa2619760198b1f5d0397d698d5))

- *(service)* Add emergency TalkBack recovery logic with plain-Japanese guidance ([25ce8e4](25ce8e4a5b0fe467dae904b9cfe2a0de75c55851))

- *(braille)* Add dedicated Braille device pairing dialog and service integration ([4c1b880](4c1b8806cae6192ddd0aff1d9d33c7131b542e77))

- Add Japanese programming AI coding concepts file ([de4a96b](de4a96b59f7d675375773874d54d519fe9050a7d))

- Add emergency watchdog service with Morse code R vibration recovery ([0345888](0345888581796ffefa1f8466ede7c8bbc5d42337))

- Suppress watchdog emergency recovery when keyguard locked and main process alive ([23bd875](23bd875a4d34958b74e4d680e0321d9bc686ac0e))

- Implement Morse code feedback logic based on updated nako3 specification ([a44e66d](a44e66d026c59f6b489d6188d3fc73304c5725dd))

- Sync support screen text with Support-System.nako3 specification ([cf46561](cf46561fdd832ddfefe3167e5bef0c218ae88103))

- Add IME keyboard guard clause and braille haptic setting toggle in MainActivity ([34eb9c7](34eb9c78625d1ccad9026abdd091522aee09c5c9))

- Implement traditional and standard Japanese phonetic dictionary toggle ([bf68ec9](bf68ec976653cbdb461fca0a7f5e9520f26841e9))

- Align ProcessorPhoneticLetters with TalkBack spec using LocaleSpan and InputMethodSubtype ([afa8ed4](afa8ed4041b661c31da10edc89d7dd31f020c5dc))

- Align ProcessorPhoneticLetters language detection with TalkBack using LocaleSpan and InputMethodSubtype ([2d8dacb](2d8dacbd090625638876b76817c90036bb104846))

- Prioritize keyboard-provided phonetic text with fallback to HelloReader dictionary and fix 0.5s delay timeline ([8b32d9a](8b32d9a482bf529ecbf45b85c79b195b2cc01415))

- Modularize Gboard accessibility node parsing into dedicated GboardProfile ([f4f0cb0](f4f0cb01e1b453238b8e9d0302fa929bc70a0196))

- *(changelog)* Initialize git-cliff with cliff.toml and CHANGELOG.md ([296e071](296e07156ba8db71d9017c0b9dedcc3d2a1d1388))

<!-- generated by git-cliff -->
