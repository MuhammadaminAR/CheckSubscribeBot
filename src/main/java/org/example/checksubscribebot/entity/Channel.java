package org.example.checksubscribebot.entity;

import lombok.Data;
import jakarta.persistence.*;
import java.util.Objects;

@Entity
@Data
public class Channel {
    @Id
    private Long id;
    private String name;
    private String inviteLink;
    private boolean isSpecial = false;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Channel channel = (Channel) o;
        return Objects.equals(id, channel.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}