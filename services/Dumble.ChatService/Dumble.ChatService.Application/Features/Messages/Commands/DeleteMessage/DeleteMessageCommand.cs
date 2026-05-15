using MediatR;

namespace Dumble.ChatService.Application.Features.Messages.Commands.DeleteMessage;

public sealed record DeleteMessageCommand(
    string MessageId,
    string CallerId
) : IRequest;
