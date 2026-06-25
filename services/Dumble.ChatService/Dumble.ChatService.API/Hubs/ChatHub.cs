using MediatR;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.SignalR;
using Dumble.ChatService.Application.Contracts;
using Dumble.ChatService.Application.Features.Messages.Commands.SendMessage;

namespace Dumble.ChatService.API.Hubs;

[Authorize]
public class ChatHub : Hub
{
    private readonly IMediator _mediator;
    private readonly IPresenceService _presenceService;
    private readonly IConversationRepository _conversationRepository;

    public ChatHub(
        IMediator mediator,
        IPresenceService presenceService,
        IConversationRepository conversationRepository)
    {
        _mediator = mediator;
        _presenceService = presenceService;
        _conversationRepository = conversationRepository;
    }

    public override async Task OnConnectedAsync()
    {
        // The platform issues a short-lived JWT with purpose=hub specifically
        // for SignalR connections so a leaked API token can't be repurposed
        // for unbounded real-time access. Reject any token that doesn't carry
        // the marker before we register presence or join groups.
        var purpose = Context.User?.FindFirst("purpose")?.Value;
        if (!string.Equals(purpose, "hub", StringComparison.Ordinal))
        {
            throw new HubException("Hub connections require a purpose=hub token (POST /api/auth/hub-token)");
        }

        var userId = Context.UserIdentifier;
        if (userId is not null)
        {
            // Only announce UserOnline on the offline→online transition, so a
            // reconnect (or a second device) doesn't spam the event.
            var becameOnline = await _presenceService.SetOnlineAsync(userId, Context.ConnectionId);
            if (becameOnline)
                await Clients.Others.SendAsync("UserOnline", new { userId });
        }
        await base.OnConnectedAsync();
    }

    public override async Task OnDisconnectedAsync(Exception? exception)
    {
        var userId = Context.UserIdentifier;
        if (userId is not null)
        {
            // Only announce UserOffline when the user's LAST connection goes away,
            // so a transient reconnect (overlapping sockets) doesn't flap presence.
            var becameOffline = await _presenceService.SetOfflineAsync(userId, Context.ConnectionId);
            if (becameOffline)
                await Clients.Others.SendAsync("UserOffline", new { userId });
        }
        await base.OnDisconnectedAsync(exception);
    }

    public async Task JoinConversation(string conversationId)
    {
        var userId = Context.UserIdentifier;
        if (userId is null) throw new HubException("Not authenticated");

        var conversation = await _conversationRepository.GetByIdAsync(conversationId)
            ?? throw new HubException($"Conversation '{conversationId}' not found");

        if (!conversation.Participants.Any(p => p.UserId == userId))
            throw new HubException("You are not a participant in this conversation");

        await Groups.AddToGroupAsync(Context.ConnectionId, conversationId);
    }

    public async Task LeaveConversation(string conversationId)
    {
        await Groups.RemoveFromGroupAsync(Context.ConnectionId, conversationId);
    }

    public async Task SendMessage(string conversationId, string content, string? replyToId)
    {
        var userId = Context.UserIdentifier!;
        var userName = Context.User?.FindFirst("displayName")?.Value ?? "User";
        var profileImage = Context.User?.FindFirst("profileImage")?.Value;

        await _mediator.Send(new SendMessageCommand(
            conversationId, userId, userName, profileImage, content, replyToId));
    }

    public async Task StartTyping(string conversationId)
    {
        var userId = Context.UserIdentifier!;
        var displayName = Context.User?.FindFirst("displayName")?.Value ?? "User";

        await _presenceService.SetTypingAsync(conversationId, userId);
        await Clients.Group(conversationId).SendAsync("UserTyping", new { conversationId, userId, displayName });
    }

    public async Task StopTyping(string conversationId)
    {
        var userId = Context.UserIdentifier!;
        await Clients.Group(conversationId).SendAsync("UserStoppedTyping", new { conversationId, userId });
    }

    public async Task Heartbeat()
    {
        var userId = Context.UserIdentifier;
        if (userId is not null)
            await _presenceService.SetOnlineAsync(userId, Context.ConnectionId);
    }
}
