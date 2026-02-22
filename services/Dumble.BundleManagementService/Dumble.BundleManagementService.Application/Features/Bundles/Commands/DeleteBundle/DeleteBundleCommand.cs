using MediatR;

namespace Dumble.BundleManagementService.Application.Features.Bundles.Commands.DeleteBundle;

public sealed record DeleteBundleCommand(Guid Id) : IRequest;