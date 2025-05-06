package org.example.checksubscribebot.repos;

import org.example.checksubscribebot.entity.Channel;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChannelRepository extends JpaRepository<Channel, Long> {
}