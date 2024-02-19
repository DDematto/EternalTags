package xyz.oribuin.eternaltags.command.impl;

import dev.rosewood.rosegarden.RosePlugin;
import dev.rosewood.rosegarden.command.framework.ArgumentsDefinition;
import dev.rosewood.rosegarden.command.framework.BaseRoseCommand;
import dev.rosewood.rosegarden.command.framework.CommandContext;
import dev.rosewood.rosegarden.command.framework.CommandInfo;
import dev.rosewood.rosegarden.command.framework.annotation.RoseExecutable;
import dev.rosewood.rosegarden.utils.StringPlaceholders;
import org.bukkit.command.CommandSender;
import xyz.oribuin.eternaltags.command.argument.TagsArgumentHandler;
import xyz.oribuin.eternaltags.manager.LocaleManager;
import xyz.oribuin.eternaltags.manager.TagsManager;
import xyz.oribuin.eternaltags.obj.Tag;

public class DeleteCommand extends BaseRoseCommand {

    public DeleteCommand(RosePlugin rosePlugin) {
        super(rosePlugin);
    }

    @RoseExecutable
    public void execute(CommandContext context) {
        LocaleManager locale = this.rosePlugin.getManager(LocaleManager.class);
        TagsManager manager = this.rosePlugin.getManager(TagsManager.class);
        CommandSender sender = context.getSender();
        Tag tag = context.get("tag");

        if (tag == null) {
            locale.sendMessage(sender, "tag-doesnt-exist");
            return;
        }

        manager.deleteTag(tag);
        locale.sendMessage(sender, "command-delete-deleted", StringPlaceholders.of("tag", manager.getDisplayTag(tag, null)));
    }

    @Override
    protected CommandInfo createCommandInfo() {
        return CommandInfo.builder("delete")
                .descriptionKey("command-delete-description")
                .permission("eternaltags.delete")
                .build();
    }

    @Override
    protected ArgumentsDefinition createArgumentsDefinition() {
        return ArgumentsDefinition.builder()
                .required("tag", new TagsArgumentHandler())
                .build();
    }

}
