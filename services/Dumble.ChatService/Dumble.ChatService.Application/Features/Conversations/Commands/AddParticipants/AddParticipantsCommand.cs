using MediatR;

namespace Dumble.ChatService.Application.Features.Conversations.Commands.AddParticipants;

public sealed record AddParticipantsCommand(
    string ConversationId,
    List<string> UserIds
) : IRequest;
