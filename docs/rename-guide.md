# Rename Guide

Follow this checklist to clone and rename the template cleanly.

## 1) Update metadata

Edit `gradle.properties` and set:

- `plugin_group`
- `plugin_name`
- `plugin_version`
- `plugin_description`
- `plugin_author_names` / `plugin_author_emails` / `plugin_author_urls`
- `plugin_website`
- `plugin_main`

`plugin_main` should point to your new entrypoint class.

## 2) Rename packages and entrypoint

- Rename the Java package from `net.ofatech.controlcore` to your own.
- Rename `TemplatePlugin` to your mod/plugin name.
- Update references in `plugin_main` and any imports.

## 3) Update visible labels

- Update `README.md` to match your project name and description.
- Replace `TemplateAPI` and the example command/event once you have your own.

## 4) Sanity check

- Ensure all imports compile after the rename.
- Keep the plugin entrypoint thin and push logic into `core` and `platform`.

