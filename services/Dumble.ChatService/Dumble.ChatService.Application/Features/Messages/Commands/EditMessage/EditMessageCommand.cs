using MediatR;

namespace Dumble.ChatService.Application.Features.Messages.Commands.EditMessage;

public sealed record EditMessageCommand(
    string MessageId,
    string CallerId,
    string NewContent
) : IRequest;
