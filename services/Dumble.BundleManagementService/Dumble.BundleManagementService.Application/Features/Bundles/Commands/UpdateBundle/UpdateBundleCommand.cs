using Dumble.BundleManagementService.Domain.BundleAggregate.ValueObjects;
using MediatR;

namespace Dumble.BundleManagementService.Application.Features.Bundles.Commands.UpdateBundle;

public record UpdateBundleCommand(
        Guid Id
    ) : IRequest;