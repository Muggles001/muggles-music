package top.boluofan.musictv.player.session;

public final class MediaControllerAccessPolicy {
    private MediaControllerAccessPolicy() {
    }

    public static boolean grantsPrivateCommands(
            String applicationPackage, String controllerPackage) {
        return applicationPackage != null && applicationPackage.equals(controllerPackage);
    }
}
