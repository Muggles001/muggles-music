package top.boluofan.musictv.catalog.model;

public final class RankingBoard {
    private final String id;
    private final String name;

    public RankingBoard(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
