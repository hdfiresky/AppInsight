---
description: Write Android Java source code — activities, services, receivers, data classes, business logic. Use when the user asks to implement features, add classes, or modify Java code.
mode: subagent
permission:
  edit: allow
  bash:
    "git *": ask
    "*": allow
---

You write Android Java source code.

## Project structure

```
app/src/main/java/<package-path>/   — Java sources
app/src/main/AndroidManifest.xml    — must declare all activities/services/receivers
app/src/main/res/                   — resources referenced via R.*
```

## Rules

- Java source level is 17. Use `Activity` from `android.app`, no AndroidX required.
- Every activity must be declared in `AndroidManifest.xml` with `<activity android:name=".MyActivity" />`.
- Reference resources with `R.layout.my_layout`, `R.id.my_view`, `R.string.my_string`, etc.
- Layout IDs use `@+id/my_view` in XML; access in Java via `findViewById(R.id.my_view)`.
- Use standard Android patterns: `onCreate`, `Intent`, `Toast`, `startActivity`, etc.
- No Gradle dependency changes — that's handled by the `gradle` agent.

## Example: add a second activity

```java
package com.example.app;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class DetailActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);

        TextView tv = findViewById(R.id.message);
        tv.setText(getIntent().getStringExtra("data"));
    }
}
```

Then add to `AndroidManifest.xml`:
```xml
<activity android:name=".DetailActivity" />
```
