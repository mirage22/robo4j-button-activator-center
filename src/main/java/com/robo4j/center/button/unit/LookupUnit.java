/*
 * Copyright (c) 2014, 2018, Marcus Hirt, Miroslav Wengner
 *
 * Robo4J is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Robo4J is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Robo4J. If not, see <http://www.gnu.org/licenses/>.
 */

package com.robo4j.center.button.unit;

import com.robo4j.ConfigurationException;
import com.robo4j.RoboContext;
import com.robo4j.RoboReference;
import com.robo4j.RoboUnit;

import com.robo4j.center.button.model.DescRawElement;
import com.robo4j.center.button.table.TableViewProcessor;
import com.robo4j.configuration.Configuration;
import com.robo4j.logging.SimpleLoggingUtil;
import com.robo4j.net.LookupServiceProvider;
import com.robo4j.net.RoboContextDescriptor;
import com.robo4j.socket.http.codec.StringMessage;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.util.Callback;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * @author Marcus Hirt (@hirt)
 * @author Miroslav Wengner (@miragemiko)
 */
public class LookupUnit extends RoboUnit<Integer> implements TableViewProcessor<DescRawElement> {

    public static final String NAME = "lookupProcessor";
    public static final String PROPERTY_DELAY = "delay";
    public static final String PROPERTY_INTERVAL = "interval";
    public static final String METADATA_UNIT_HTTP_CONF = "unitConf";
    public static final String METADATA_UNIT_PROCESSOR = "unitProcessor";
    public static final String METADATA_DESC = "desc";
    private static final int PORT_RANGE_START = 11000;
    public static final String METADATA_IP = "ip";
    private final static String BUTTON_TEXT_ACTION = "Action";
    private final static String BUTTON_TEXT_DISABLE = "Disable";

    /* staring port range*/
    private volatile AtomicInteger lastPortInRange = new AtomicInteger(PORT_RANGE_START);
    private TableView<DescRawElement> systemTableView;
    private Map<String, RoboContextDescriptor> discoveredContexts = new ConcurrentHashMap<>();
    private Map<String, Button> singleContextButton = new HashMap<>();
    private long delay;
    private long interval;

    public LookupUnit(RoboContext context, String id) {
        super(Integer.class, context, id);
    }

    @Override
    protected void onInitialization(Configuration configuration) throws ConfigurationException {

        delay = configuration.getLong(PROPERTY_DELAY, 2L);
        interval = configuration.getLong(PROPERTY_INTERVAL, 2L);
        try {
            LookupServiceProvider.getDefaultLookupService().start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @Override
    public void start() {

        ObservableList<DescRawElement> data = FXCollections.observableArrayList();
        createHeaderTableView();
        getContext().getScheduler().scheduleAtFixedRate(() -> {
            //@formatter:off

            Map<String, RoboContextDescriptor> map = LookupServiceProvider.getDefaultLookupService().getDiscoveredContexts();
            List<DescRawElement> netData = map
                    .entrySet().stream()
                    .filter(e -> {
                        return e.getValue().getMetadata().size() != 0 &&
                            !Boolean.valueOf(e.getValue().getMetadata().get("internal")) &&
                            Boolean.valueOf(e.getValue().getMetadata().get("isButton"));
                            })
                    .map(e -> {
                        String context = e.getKey();
                        String unitProcessor = e.getValue().getMetadata().get(METADATA_UNIT_PROCESSOR);
                        String desc = e.getValue().getMetadata().get(METADATA_DESC);
                        return new DescRawElement(context, unitProcessor, desc);
                    })
                    .collect(Collectors.toList());

            if(systemTableView.getItems().size() != netData.size() || !systemTableView.getItems().containsAll(netData)){
                systemTableView.getItems().clear();
                systemTableView.getItems().addAll(netData);
                map.forEach((key, value) -> {
                    discoveredContexts.putIfAbsent(key, value);
                });
            }
            //@formatter:on

        }, delay, interval, TimeUnit.SECONDS);

        systemTableView.setItems(data);
    }


    @SuppressWarnings("unchecked")
    private void createHeaderTableView() {
        TableColumn nameCol = new TableColumn("Context:");
        nameCol.setMinWidth(200);
        nameCol.setCellValueFactory(
                new PropertyValueFactory<DescRawElement, String>(DescRawElement.KEY_NAME));

        TableColumn valueCol = new TableColumn("Processor:");
        valueCol.setMinWidth(120);
        valueCol.setCellValueFactory(new PropertyValueFactory<DescRawElement, String>(DescRawElement.KEY_VALUE));

        TableColumn descCol = new TableColumn("Desc.:");
        descCol.setMinWidth(120);
        descCol.setCellValueFactory(new PropertyValueFactory<DescRawElement, String>(DescRawElement.KEY_DESC));
        TableColumn<DescRawElement, Void> buttonCol = new TableColumn("Button:");
        buttonCol.setMinWidth(50);

        Callback<TableColumn<DescRawElement, Void>, TableCell<DescRawElement, Void>> cellFactory = (param) ->
                new TableCell<DescRawElement, Void>() {

                    private final Button btn = new Button(BUTTON_TEXT_ACTION);

                    {
                        btn.setOnAction((ActionEvent event) -> {
                            DescRawElement data = getTableView().getItems().get(getIndex());
                            singleContextButton.putIfAbsent(data.getName(), btn);
                            btn.setText(BUTTON_TEXT_ACTION);
                            processButton(data.getName(), data.getValue());
                        });
                    }

                    @Override
                    public void updateItem(Void item, boolean empty) {
                        super.updateItem(item, empty);
                        if (empty) {
                            setGraphic(null);
                        } else {
                            setGraphic(btn);
                        }
                    }

                };

        buttonCol.setCellFactory(cellFactory);


        systemTableView.getColumns().addAll(nameCol, valueCol, descCol, buttonCol);
    }

    @Override
    public void setTableView(TableView<DescRawElement> tableView) {
        this.systemTableView = tableView;
    }


    private void processButton(String contextName, String controllerName) {
        //TODO implement logic
        StringMessage pushMessage = new StringMessage("push");
        RoboReference<StringMessage> controller = LookupServiceProvider.getDefaultLookupService().getContext(contextName).getReference(controllerName);
        controller.sendMessage(pushMessage);
    }


}
