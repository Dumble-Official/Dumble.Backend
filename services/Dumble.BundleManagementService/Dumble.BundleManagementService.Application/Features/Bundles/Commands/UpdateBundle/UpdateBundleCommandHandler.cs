using Dumble.BundleManagementService.Application.Contracts;
using Dumble.BundleManagementService.Application.Contracts.Repositories;
using Dumble.BundleManagementService.Application.Identity;
using Dumble.BundleManagementService.Domain.BundleAggregate;
using Dumble.BundleManagementService.Domain.BundleAggregate.Enums;
using Dumble.BundleManagementService.Domain.BundleAggregate.ValueObjects;
using Dumble.SharedKernel.Contracts;
using MediatR;
using Name = Dumble.BundleManagementService.Domain.BundleAggregate.ValueObjects.Name;

namespace Dumble.BundleManagementService.Application.Features.Bundles.Commands.UpdateBundle;

internal sealed class UpdateBundleCommandHandler(
    ILogger<UpdateBundleCommandHandler> logger,
    IFileService fileService,
    IGenericRepository<Bundle, BundleId> bundlesRepository,
    ILoggedInUserService loggedInUserService) : IRequestHandler<UpdateBundleCommand>
{
    public async Task Handle(UpdateBundleCommand request, CancellationToken cancellationToken)
    {
        var loggedInUser = loggedInUserService.GetCurrentUser();

        var bundle = await bundlesRepository.Get(BundleId.Create(request.Id))
            ?? throw new KeyNotFoundException($"Bundle {request.Id} not found");

        var currentUserAccountId = AccountId.Create(AccountIdentity.ToAccountGuid(loggedInUser.Id));

        if (!currentUserAccountId.Equals(bundle.OwnerId))
            throw new UnauthorizedAccessException("You can only update your own bundles");

        bundlesRepository.Update(bundle);
        await bundlesRepository.CompleteAsync();
    }
}
