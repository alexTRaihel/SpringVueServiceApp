package com.fitness.app.services.impl;

import com.fitness.app.dto.EventType;
import com.fitness.app.dto.MessagePageDto;
import com.fitness.app.dto.MetaDto;
import com.fitness.app.dto.ObjectType;
import com.fitness.app.models.Message;
import com.fitness.app.models.Views;
import com.fitness.app.repo.MessagesRepo;
import com.fitness.app.services.MessageService;
import com.fitness.app.utils.WsSender;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MessageServiceImpl implements MessageService {

    private static String URL_PATTERN = "https?:\\/\\/?[\\w\\d\\._\\-%\\/\\?=&#]+";
    private static String IMG_PATTERN = "\\.(jpeg|jpg|gif|png)$";

    private static Pattern URL_REGEX = Pattern.compile(URL_PATTERN, Pattern.CASE_INSENSITIVE);
    private static Pattern IMG_REGEX = Pattern.compile(IMG_PATTERN, Pattern.CASE_INSENSITIVE);

    private final MessagesRepo messagesRepo;
    private final BiConsumer<EventType, Message> wsSender;

    public MessageServiceImpl(MessagesRepo messagesRepo, WsSender wsSender) {
        this.messagesRepo = messagesRepo;
        this.wsSender = wsSender.getSender(ObjectType.MESSAGE, Views.IdName.class);
    }

    public MessagePageDto findAll(Pageable pageable){
        Page<Message> page = messagesRepo.findAll(pageable);
        return new MessagePageDto(
                page.getContent(),
                pageable.getPageNumber(),
                page.getTotalPages()
        );
    }

    public Message save(Message message) throws IOException {
        fillMeta(message);
        Message createdMessage = messagesRepo.save(message);
        wsSender.accept(EventType.CREATE, createdMessage);
        return createdMessage;
    }

    @Override
    public Message update(Message message, Message messageFromDB) throws IOException {
        BeanUtils.copyProperties(message, messageFromDB, "id", "comments", "author", "localDateTime");
        fillMeta(messageFromDB);
        Message updatedMessage = messagesRepo.save(messageFromDB);
        wsSender.accept(EventType.UPDATE, updatedMessage);
        return updatedMessage;
    }

    @Override
    public void delete(Message message) {
        messagesRepo.delete(message);
        wsSender.accept(EventType.REMOVE, message);
    }

    private void fillMeta(Message message) throws IOException {

        String text = message.getName();
        Matcher matcher = URL_REGEX.matcher(text);

        if (matcher.find()){
            String url = text.substring(matcher.start(), matcher.end());
            matcher = IMG_REGEX.matcher(url);
            message.setLink(url);
            if (matcher.find()){
                message.setLinkCover(url);
            } else if (!url.contains("youto")){
                MetaDto meta = getMeta(url);
                message.setLinkCover(meta.getCover());
                message.setLinkTitle(meta.getTitle());
                message.setDescription(meta.getDescription());
            }
        }
    }

    private MetaDto getMeta(String url) throws IOException{
        Document doc = Jsoup.connect(url).get();
        Elements title = doc.select("meta[name$=title],meta[property$=title]");
        Elements description = doc.select("meta[name$=description],meta[property$=description]");
        Elements cover = doc.select("meta[name$=image],meta[property$=image]");

        return new MetaDto(
                getContent(title.first()),
                getContent(description.first()),
                getContent(cover.first())
        );
    }

    private String getContent(Element element){
        return element == null ? "" : element.attr("content");
    }
}
