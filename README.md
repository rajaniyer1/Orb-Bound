# Orb Bound: Gesture-Based Idle RPG

## Project Overview

Orb Bound is a native Android game built entirely in Java, featuring a custom 60 FPS rendering engine and advanced multithreading. Moving beyond standard event-driven Android XML layouts, this project implements a continuous game loop utilizing Android's `SurfaceView` and `Canvas` APIs. 

The application is a progression-based idle game where players earn currency (Orbs) by performing precise, time-sensitive touch gestures. The project demonstrates a strong understanding of state management, concurrent processing, real-time rendering, and interactive UI/UX design.

## Key Features

* **Dynamic Gesture Recognition:** Implements a custom implementation of `GestureDetector` to track sequential taps and directional swipes in real-time.
* **Custom Physics & Particle Engine:** Features mathematical calculations for floating entities, dynamic background elements (ambient stars), and gravity-based particle explosions upon successful interactions.
* **Time-Sensitive Mechanics:** Includes a procedural "fuse" timer that scales dynamically and requires users to complete complex gesture sequences before time expires.
* **Combo & Multiplier System:** Rewards consecutive successes with scaling multipliers, enhancing the risk/reward loop.
* **Idle Economy & Upgrades:** Players can purchase passive upgrades (Wisps, Relics, Void Cores) that generate currency automatically in the background.
* **Advanced Game Feel (Juice):** Incorporates screen shake algorithms, pulsating UI animations, and clear visual feedback for success and failure states.

## Technical Architecture

This project was architected to solve the common performance pitfalls of standard Android UI development when applied to game mechanics. 

### 1. Custom Rendering Pipeline (SurfaceView)
Instead of relying on the main UI thread to update XML layouts, the game action takes place on a `SurfaceView`. A dedicated `GameThread` handles the `update()` and `draw()` lifecycle at a target of 60 frames per second. This allows for smooth rotational matrix transformations, custom path effects (e.g., dashed glowing rings), and alpha-fading particle systems.

### 2. Multithreading & Concurrency
The application concurrently runs the main Android UI thread (handling the shop overlay and score updates) alongside the background game loop. To prevent `ConcurrentModificationException` errors and ensure thread safety, all physics objects, input sequences, and particle lists are managed using `CopyOnWriteArrayList`.

### 3. Hybrid UI Approach
* **Game Canvas:** Handles all high-performance rendering (physics, gestures, particles).
* **Android Layout Overlays:** Uses standard Android Layouts (`FrameLayout`, `LinearLayout`) stacked on top of the SurfaceView to handle complex scrolling menus and shop interfaces.
* **Programmatic View Injection:** Demonstrates the ability to bypass XML entirely by dynamically instantiating, styling, and injecting Android `View` components (such as the version watermark) directly into the root layout at runtime.

### 4. Mathematical Animations
Rather than relying solely on `ObjectAnimator`, the game features programmatic trigonometric animations (using sine waves for floating entities) and velocity-based vector math for particle trajectories and screen shake displacement.

## Installation & Setup

1.  Clone the repository to your local machine.
2.  Open the project in Android Studio (Arctic Fox or newer recommended).
3.  Ensure your Android SDK is up to date (Minimum API 24).
4.  Sync the project with Gradle files.
5.  Build and run the application on an emulator or a physical Android device.

## How to Play

1.  **Cast Spells:** Observe the target sequence displayed at the top of the casting circle. Perform the exact sequence of Taps, Swipes (Up, Down, Left, Right) on the screen.
2.  **Beat the Timer:** Complete the sequence before the outer ring depletes. Failing an input or running out of time resets your combo.
3.  **Overcharge Mode:** Reaching 100 Orbs unlocks longer, more complex sequences for higher base rewards.
4.  **Visit the Shop:** Tap the "SHOP" button in the bottom right corner to spend Orbs on passive generators.
5.  **Build an Idle Economy:** Purchased entities will float on the screen and generate passive Orbs every second, even while you are actively casting.

## Future Enhancements

* **Persistent State Management:** Implementation of `SharedPreferences` or SQLite to save player progress, currency, and owned upgrades between sessions.
* **Offline Progress Calculation:** Timestamp tracking to calculate and award passive income generated while the application is closed.
* **Audio Engine Integration:** Utilizing Android's `SoundPool` for low-latency sound effects on gestures and `MediaPlayer` for ambient background tracks.

---
*Developed as a portfolio project showcasing advanced Android Java development, custom rendering, and concurrent programming.*
