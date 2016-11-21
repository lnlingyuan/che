/*******************************************************************************
 * Copyright (c) 2012-2016 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 *******************************************************************************/
package org.eclipse.che.ide.command.explorer.old;

import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.AcceptsOneWidget;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.web.bindery.event.shared.EventBus;

import org.eclipse.che.api.promises.client.Operation;
import org.eclipse.che.api.promises.client.OperationException;
import org.eclipse.che.api.promises.client.PromiseError;
import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.ide.api.command.ApplicableContext;
import org.eclipse.che.ide.api.command.CommandManager3;
import org.eclipse.che.ide.api.command.CommandType;
import org.eclipse.che.ide.api.command.CommandTypeRegistry;
import org.eclipse.che.ide.api.command.ContextualCommand;
import org.eclipse.che.ide.api.machine.events.WsAgentStateEvent;
import org.eclipse.che.ide.api.machine.events.WsAgentStateHandler;
import org.eclipse.che.ide.api.parts.WorkspaceAgent;
import org.eclipse.che.ide.api.parts.base.BasePresenter;
import org.eclipse.che.ide.command.editor.page.CommandEditorPage;
import org.eclipse.che.ide.command.editor.page.arguments.ArgumentsPage;
import org.eclipse.che.ide.command.editor.page.info.InfoPage;
import org.eclipse.che.ide.command.editor.page.previewurl.PreviewUrlPage;
import org.vectomatic.dom.svg.ui.SVGResource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.eclipse.che.ide.api.parts.PartStackType.NAVIGATION;

/**
 * Presenter for managing commands.
 *
 * @author Artem Zatsarynnyi
 */
@Singleton
public class CommandsExplorerPresenter extends BasePresenter implements CommandsExplorerView.ActionDelegate,
                                                                        WsAgentStateHandler,
                                                                        CommandManager3.CommandChangedListener,
                                                                        CommandEditorPage.DirtyStateListener {

    private static final String TITLE   = "Commands";
    private static final String TOOLTIP = "Manage commands";

    private final CommandsExplorerView view;
    private final WorkspaceAgent       workspaceAgent;
    private final CommandManager3      commandManager;
    private final CommandTypeRegistry  commandTypeRegistry;

    private final List<CommandEditorPage> pages;

    // stores initial name of the currently edited command
    private String editedCommandNameInitial;

    @Inject
    public CommandsExplorerPresenter(CommandsExplorerView view,
                                     WorkspaceAgent workspaceAgent,
                                     EventBus eventBus,
                                     CommandManager3 commandManager,
                                     CommandTypeRegistry commandTypeRegistry,
                                     InfoPage infoPage,
                                     ArgumentsPage argumentsPage,
                                     PreviewUrlPage previewUrlPage) {
        this.view = view;
        this.workspaceAgent = workspaceAgent;
        this.commandManager = commandManager;
        this.commandTypeRegistry = commandTypeRegistry;

        view.setDelegate(this);

        eventBus.addHandler(WsAgentStateEvent.TYPE, this);

        commandManager.addCommandChangedListener(this);

        pages = new ArrayList<>();
        pages.add(infoPage);
        pages.add(argumentsPage);
        pages.add(previewUrlPage);
    }

    @Override
    public void onOpen() {
        super.onOpen();

        refreshView();
    }

    @Override
    public int getSize() {
        return 900;
    }

    @Override
    public void go(AcceptsOneWidget container) {
        for (CommandEditorPage page : pages) {
            view.addPage(page.getView(), page.getTitle(), page.getTooltip());
        }

        container.setWidget(getView());
    }

    @Override
    public String getTitle() {
        return TITLE;
    }

    @Override
    public void setVisible(boolean visible) {
    }

    @Override
    public IsWidget getView() {
        return view;
    }

    @Nullable
    @Override
    public String getTitleToolTip() {
        return TOOLTIP;
    }

    @Nullable
    @Override
    public SVGResource getTitleImage() {
        return null;
    }

    @Override
    public void onCommandTypeSelected(CommandType commandType) {
        // TODO: should we hide pages?
    }

    @Override
    public void onCommandSelected(ContextualCommand command) {
        // save initial value of the edited command name
        // in order to be able to detect whether the command was renamed during editing or not
        editedCommandNameInitial = command.getName();

        // initialize all pages with the selected command
        for (CommandEditorPage page : pages) {
            page.setDirtyStateListener(this);
            page.setCommand(command);
        }

        onDirtyStateChanged();
    }

    @Override
    public void onCommandRevert(ContextualCommand command) {
    }

    @Override
    public void onCommandSave(ContextualCommand command) {
        commandManager.updateCommand(editedCommandNameInitial, command).catchError(new Operation<PromiseError>() {
            @Override
            public void apply(PromiseError arg) throws OperationException {
                // TODO: replace it with notification
                Window.alert(arg.getMessage());
            }
        });
    }

    @Override
    public void onCommandAdd() {
        final ApplicableContext applicableContext = new ApplicableContext();
        // by default, command should be applicable to the workspace only
        applicableContext.setWorkspaceApplicable(true);

        final CommandType selectedCommandType = view.getSelectedCommandType();
        if (selectedCommandType != null) {
            commandManager.createCommand(selectedCommandType.getId(), applicableContext).then(new Operation<ContextualCommand>() {
                @Override
                public void apply(ContextualCommand arg) throws OperationException {
                    view.selectCommand(arg);
                }
            }).catchError(new Operation<PromiseError>() {
                @Override
                public void apply(PromiseError arg) throws OperationException {
                    // TODO: replace it with notification
                    Window.alert(arg.getMessage());
                }
            });
        }
    }

    @Override
    public void onCommandDuplicate(ContextualCommand command) {
        commandManager.createCommand(command).then(new Operation<ContextualCommand>() {
            @Override
            public void apply(ContextualCommand arg) throws OperationException {
                view.selectCommand(arg);
            }
        }).catchError(new Operation<PromiseError>() {
            @Override
            public void apply(PromiseError arg) throws OperationException {
                // TODO: replace it with notification
                Window.alert(arg.getMessage());
            }
        });
    }

    @Override
    public void onCommandRemove(ContextualCommand command) {
        commandManager.removeCommand(command.getName()).then(new Operation<Void>() {
            @Override
            public void apply(Void arg) throws OperationException {
                // TODO: select another command
            }
        }).catchError(new Operation<PromiseError>() {
            @Override
            public void apply(PromiseError arg) throws OperationException {
                // TODO: replace it with notification
                Window.alert(arg.getMessage());
            }
        });
    }

    @Override
    public void onWsAgentStarted(WsAgentStateEvent event) {
        workspaceAgent.openPart(this, NAVIGATION);
        workspaceAgent.setActivePart(this);
    }

    @Override
    public void onWsAgentStopped(WsAgentStateEvent event) {
    }

    @Override
    public void onCommandAdded(ContextualCommand command) {
        refreshView();
    }

    @Override
    public void onCommandUpdated(ContextualCommand command) {
        refreshView();
    }

    @Override
    public void onCommandRemoved(ContextualCommand command) {
        refreshView();
    }

    private void refreshView() {
        Map<CommandType, List<ContextualCommand>> commands = new HashMap<>();

        // all registered command types need to be shown in view
        // so populate map by all registered command types
        for (CommandType commandType : commandTypeRegistry.getCommandTypes()) {
            commands.put(commandType, new ArrayList<ContextualCommand>());
        }

        for (ContextualCommand command : commandManager.getCommands()) {
            final CommandType commandType = commandTypeRegistry.getCommandTypeById(command.getType());

            List<ContextualCommand> commandsByType = commands.get(commandType);
            if (commandsByType == null) {
                commandsByType = new ArrayList<>();
                commands.put(commandType, commandsByType);
            }

            commandsByType.add(command);
        }

        view.setCommands(commands);
    }

    @Override
    public void onDirtyStateChanged() {
        for (CommandEditorPage page : pages) {
            // if at least one page is modified
            if (page.isDirty()) {
                view.setSaveEnabled(true);
                return;
            }
        }

        view.setSaveEnabled(false);
    }
}