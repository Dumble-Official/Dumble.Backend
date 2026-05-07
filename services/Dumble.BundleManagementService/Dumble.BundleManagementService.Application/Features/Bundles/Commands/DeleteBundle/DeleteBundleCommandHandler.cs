using Dumble.BundleManagementService.Application.Contracts;
using Dumble.BundleManagementService.Application.Contracts.Repositories;
using Dumble.BundleManagementService.Application.Identity;
using Dumble.BundleManagementService.Domain.BundleAggregate;
using Dumble.BundleManagementService.Domain.BundleAggregate.ValueObjects;
using MediatR;

namespace Dumble.BundleManagementService.Application.Features.Bundles.Commands.DeleteBundle;

internal sealed class DeleteBundleCommandHandler(
    IFileService fileService,
    IGenericRepository<Bundle,BundleId> bundlesRepository, 
    ILoggedInUserService loggedInUserService) : IRequestHandler<DeleteBundleCommand>
{
    public async Task Handle(DeleteBundleCommand request, CancellationToken cancellationToken)
    {
        var loggedInUser = loggedInUserService.GetCurrentUser();

        var bundle = await bundlesRepository.Get(BundleId.Create(request.Id))
            ?? throw new KeyNotFoundException($"Bundle {request.Id} not found");

        bundle.AssertOwnedBy(AccountId.Create(AccountIdentity.ToAccountGuid(loggedInUser.Id)));

        var tasks = bundle.Images.Select(img => fileService.DeleteAsync(img.Value));

        var results = await Task.WhenAll(tasks);
        
        bundlesRepository.Delete(bundle);

        var rowsAffected = await bundlesRepository.CompleteAsync();
    }
}