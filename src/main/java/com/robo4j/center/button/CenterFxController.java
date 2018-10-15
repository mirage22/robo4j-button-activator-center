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

package com.robo4j.center.button;

import com.robo4j.ConfigurationException;
import com.robo4j.RoboBuilder;
import com.robo4j.RoboBuilderException;
import com.robo4j.RoboContext;
import com.robo4j.center.button.model.DescRawElement;
import com.robo4j.center.button.unit.LookupUnit;
import com.robo4j.configuration.Configuration;
import com.robo4j.configuration.ConfigurationBuilder;
import com.robo4j.net.LookupService;
import com.robo4j.net.LookupServiceProvider;
import com.robo4j.util.SystemUtil;
import javafx.fxml.FXML;
import javafx.scene.control.TableView;

import java.io.IOException;

/**
 * @author Marcus Hirt (@hirt)
 * @author Miroslav Wengner (@miragemiko)
 */
public class CenterFxController {

    @FXML
    private TableView<DescRawElement> systemsTV;

    private RoboContext system;

    public void init(RoboBuilder builder) throws RoboBuilderException, ConfigurationException {

        Configuration config = new ConfigurationBuilder()
                .addLong(LookupUnit.PROPERTY_DELAY, 1L)
                .build();
        LookupUnit lookupProcessor = new LookupUnit(builder.getContext(), LookupUnit.NAME);
        lookupProcessor.initialize(config);
        lookupProcessor.setTableView(systemsTV);
        builder.add(lookupProcessor);

        system = builder.build();
        system.start();

        LookupService service = LookupServiceProvider.getDefaultLookupService();
        try {
            service.start();
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println(SystemUtil.printStateReport(system));

    }

    public void stop() {
        if (system != null) {
            system.shutdown();
            System.out.println(SystemUtil.printStateReport(system));
        }
        System.out.println("Bye! ");
    }
}
