package jun.example.domain;

import lombok.Data;

import java.io.Serializable;

@Data
public class PlayerSummary implements Serializable {

    private static final long serialVersionUID = 1L;

    private long playerID;
    private String playerName;
    private int playerHead;
    private int playerPortrait;
}
