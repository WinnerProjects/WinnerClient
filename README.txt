WinnerClient standalone Nametags and StorageESP test code
Target: WinnerClient commit 5d9c2b68c0c98ec6bb8d986e064329ef1a166671, Minecraft 1.21.11, Yarn build.1, Java 21.

Back up your project. Copy the src folder into the project root and accept replacement of files. Compare replacements first if you have local changes to EventBus.java, GameRendererAccessor.java, EntityRendererMixin.java, or ClientPlayerInteractionManagerMixin.java: replacing them will discard those local changes. This archive does NOT overwrite the network mixin, XCarry, NameHider, or mixin JSON.

Open src/main/resources/winnerclient.mixins.json and add "ShaderLoaderMixin" to its client array. Keep all existing entries. Ensure EntityRendererMixin, ClientPlayerInteractionManagerMixin, and GameRendererAccessor are present (they are present in the target commit). Do not add duplicate entries. ModuleManager already registers both modules at the target commit.

The old StorageESP's pollVoidLoad is intentionally disabled. The compatibility method returns null, preventing the old network hook from sending spoofed movement packets.

Build: ./gradlew build (Windows: gradlew.bat build). These files were not compiled or tested in game. Shader compilation and Mixin application need runtime testing. Send complete compiler errors or latest.log/crash report, including the first Caused by.

These are standalone Meteor-inspired implementations, not a full feature-identical Meteor source port. Shader mode outlines boxes rather than animated storage models. Nametags uses HUD projection and lacks Meteor's custom font and full integrated friends/notification systems. FriendNames is a manual list. Some features may need compatibility fixes.

Meteor references: https://github.com/MeteorDevelopment/meteor-client/tree/4d24e0223c4895f41a33025acbc1c909a764a1d4
Meteor Client is GPL-3.0. Retain applicable notices and comply with its license if distributing adaptations.
